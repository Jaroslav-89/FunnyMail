import CryptoKit
import Foundation
import OnnxRuntimeBindings
import Shared

/// ONNX Runtime bridge for iOS. The 47 MB model is downloaded once into the app's private cache,
/// SHA-256 verified, and then kept on-device. Photo pixels are never uploaded.
final class DexiNedEdgeProvider: NSObject, NeuralEdgeInferenceProvider {
    static let shared = DexiNedEdgeProvider()

    private let lock = NSRecursiveLock()
    private var environment: ORTEnv?
    private var session: ORTSession?

    private let modelName = "edge_detection_dexined_2024sep.onnx"
    private let modelURL = URL(
        string: "https://huggingface.co/opencv/edge_detection_dexined/resolve/main/edge_detection_dexined_2024sep.onnx"
    )!
    private let expectedSHA256 =
        "a50d01dc8481549c7dedb9eb3e0123b810a016520df75e4669a504609982cdd0"

    private let inputSize = 512
    private var inputPixelCount: Int { inputSize * inputSize }

    func detect(image: GrayImage) -> EdgeMap? {
        lock.lock()
        defer { lock.unlock() }
        do {
            let currentSession = try loadSession()
            let width = Int(image.width)
            let height = Int(image.height)
            guard width >= 3, height >= 3 else { return nil }

            // Match the OpenCV Zoo reference preprocessing: NCHW, 512x512, scale 1.0,
            // B/G/R channels with means 103.5, 116.2, 123.6. V11's shared input is grayscale,
            // so luminance is replicated into the three channels before mean subtraction.
            var inputFloats = [Float](repeating: 0, count: 3 * inputPixelCount)
            let means: [Float] = [103.5, 116.2, 123.6]
            for channel in 0..<3 {
                let channelOffset = channel * inputPixelCount
                for y in 0..<inputSize {
                    let sourceY = min(height - 1, y * height / inputSize)
                    for x in 0..<inputSize {
                        let sourceX = min(width - 1, x * width / inputSize)
                        let luminance = Float(image.pixels.get(index: Int32(sourceY * width + sourceX)))
                        inputFloats[channelOffset + y * inputSize + x] = luminance - means[channel]
                    }
                }
            }

            let inputData = inputFloats.withUnsafeBufferPointer { Data(buffer: $0) }
            let inputValue = try ORTValue(
                tensorData: NSMutableData(data: inputData),
                elementType: .float,
                shape: [1, 3, inputSize, inputSize].map { NSNumber(value: $0) }
            )
            let inputName = try currentSession.inputNames().first
            guard let inputName else { return nil }
            let outputNames = try currentSession.outputNames()
            let outputs = try currentSession.run(
                withInputs: [inputName: inputValue],
                outputNames: outputNames,
                runOptions: nil
            )

            var accumulated = [Float](repeating: 0, count: inputPixelCount)
            var acceptedOutputs = 0
            for name in outputNames {
                guard let value = outputs[name] else { continue }
                let outputData = try value.tensorData() as Data
                let logits = outputData.withUnsafeBytes { rawBuffer -> [Float] in
                    Array(rawBuffer.bindMemory(to: Float.self))
                }
                guard logits.count >= inputPixelCount else { continue }

                var probabilities = [Float](repeating: 0, count: inputPixelCount)
                var minimum = Float.greatestFiniteMagnitude
                var maximum = -Float.greatestFiniteMagnitude
                for index in 0..<inputPixelCount {
                    let value = logits[index]
                    let probability = Float(1.0 / (1.0 + exp(-Double(value))))
                    probabilities[index] = probability
                    minimum = min(minimum, probability)
                    maximum = max(maximum, probability)
                }
                let range = max(maximum - minimum, 0.000001)
                for index in 0..<inputPixelCount {
                    accumulated[index] += (probabilities[index] - minimum) / range * 255
                }
                acceptedOutputs += 1
            }
            guard acceptedOutputs > 0 else { return nil }

            var confidence512 = [UInt8](repeating: 0, count: inputPixelCount)
            for index in 0..<inputPixelCount {
                confidence512[index] = UInt8(
                    min(255, max(0, Int(accumulated[index] / Float(acceptedOutputs))))
                )
            }

            // Return the model confidence map at the decoded photograph dimensions. Common code
            // applies two-threshold hysteresis before contour tracing/vectorization.
            let outputSize = width * height
            let bytes = KotlinByteArray(size: Int32(outputSize))
            for y in 0..<height {
                let sourceY = min(inputSize - 1, y * inputSize / height)
                for x in 0..<width {
                    let sourceX = min(inputSize - 1, x * inputSize / width)
                    let value = confidence512[sourceY * inputSize + sourceX]
                    bytes.set(index: Int32(y * width + x), value: Int8(bitPattern: value))
                }
            }
            return EdgeMap(width: Int32(width), height: Int32(height), strength: bytes)
        } catch {
            NSLog("DexiNed iOS inference unavailable: %@", String(describing: error))
            return nil
        }
    }

    private func loadSession() throws -> ORTSession {
        if let session { return session }
        let modelFile = try ensureModelFile()
        let env = try ORTEnv(loggingLevel: ORTLoggingLevel.warning)
        let options = try ORTSessionOptions()
        try options.setIntraOpNumThreads(2)
        let session = try ORTSession(env: env, modelPath: modelFile.path, sessionOptions: options)
        self.environment = env
        self.session = session
        return session
    }

    private func ensureModelFile() throws -> URL {
        let caches = try FileManager.default.url(
            for: .cachesDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let directory = caches.appendingPathComponent("FunnyMailEdgeModels", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let target = directory.appendingPathComponent(modelName)
        if let attributes = try? FileManager.default.attributesOfItem(atPath: target.path),
           let size = attributes[.size] as? NSNumber,
           size.int64Value > 40_000_000 {
            return target
        }

        let staging = directory.appendingPathComponent(modelName + ".download")
        try? FileManager.default.removeItem(at: staging)
        let state = DownloadState()
        let semaphore = DispatchSemaphore(value: 0)
        URLSession.shared.downloadTask(with: modelURL) { temporaryURL, response, error in
            defer { semaphore.signal() }
            if let error {
                state.error = error
                return
            }
            guard let response = response as? HTTPURLResponse,
                  (200..<300).contains(response.statusCode),
                  let temporaryURL else {
                state.error = DexiNedDownloadError.badResponse
                return
            }
            do {
                try FileManager.default.moveItem(at: temporaryURL, to: staging)
                state.file = staging
            } catch {
                state.error = error
            }
        }.resume()
        semaphore.wait()
        if let error = state.error { throw error }
        guard FileManager.default.fileExists(atPath: staging.path) else {
            throw DexiNedDownloadError.missingDownload
        }

        let fileData = try Data(contentsOf: staging, options: .mappedIfSafe)
        let checksum = SHA256.hash(data: fileData)
            .map { String(format: "%02x", $0) }
            .joined()
        guard checksum.caseInsensitiveCompare(expectedSHA256) == .orderedSame else {
            try? FileManager.default.removeItem(at: staging)
            throw DexiNedDownloadError.checksumMismatch
        }

        try? FileManager.default.removeItem(at: target)
        try FileManager.default.moveItem(at: staging, to: target)
        return target
    }
}

private final class DownloadState {
    var file: URL?
    var error: Error?
}

private enum DexiNedDownloadError: Error {
    case badResponse
    case missingDownload
    case checksumMismatch
}
