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
    private var sessionOptions: ORTSessionOptions?

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
            let means: [Float] = [103.5, 116.2, 123.6]
            var resizedLuminance = [Float](repeating: 0, count: inputPixelCount)
            for y in 0..<inputSize {
                let sourceY = min(
                    Double(height - 1),
                    max(0, (Double(y) + 0.5) * Double(height) / Double(inputSize) - 0.5)
                )
                let y0 = Int(sourceY)
                let y1 = min(height - 1, y0 + 1)
                let fy = Float(sourceY - Double(y0))
                for x in 0..<inputSize {
                    let sourceX = min(
                        Double(width - 1),
                        max(0, (Double(x) + 0.5) * Double(width) / Double(inputSize) - 0.5)
                    )
                    let x0 = Int(sourceX)
                    let x1 = min(width - 1, x0 + 1)
                    let fx = Float(sourceX - Double(x0))
                    let top = Float(image.pixels.get(index: Int32(y0 * width + x0))) * (1 - fx) +
                        Float(image.pixels.get(index: Int32(y0 * width + x1))) * fx
                    let bottom = Float(image.pixels.get(index: Int32(y1 * width + x0))) * (1 - fx) +
                        Float(image.pixels.get(index: Int32(y1 * width + x1))) * fx
                    resizedLuminance[y * inputSize + x] = top * (1 - fy) + bottom * fy
                }
            }

            var inputFloats = [Float](repeating: 0, count: 3 * inputPixelCount)
            for channel in 0..<3 {
                let channelOffset = channel * inputPixelCount
                for index in 0..<inputPixelCount {
                    inputFloats[channelOffset + index] = resizedLuminance[index] - means[channel]
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

            // Keep the decoded photo's aspect ratio and the model's native detail scale so 512px
            // model edges don't turn into double-width blocks when the source photo is 1024px.
            let outputLongSide = min(inputSize, max(width, height))
            let scale = Double(outputLongSide) / Double(max(width, height))
            let outputWidth = max(3, Int((Double(width) * scale).rounded()))
            let outputHeight = max(3, Int((Double(height) * scale).rounded()))
            let bytes = KotlinByteArray(size: Int32(outputWidth * outputHeight))
            for y in 0..<outputHeight {
                let sourceY = min(
                    Double(inputSize - 1),
                    max(0, (Double(y) + 0.5) * Double(inputSize) / Double(outputHeight) - 0.5)
                )
                let y0 = Int(sourceY)
                let y1 = min(inputSize - 1, y0 + 1)
                let fy = Float(sourceY - Double(y0))
                for x in 0..<outputWidth {
                    let sourceX = min(
                        Double(inputSize - 1),
                        max(0, (Double(x) + 0.5) * Double(inputSize) / Double(outputWidth) - 0.5)
                    )
                    let x0 = Int(sourceX)
                    let x1 = min(inputSize - 1, x0 + 1)
                    let fx = Float(sourceX - Double(x0))
                    let top = Float(confidence512[y0 * inputSize + x0]) * (1 - fx) +
                        Float(confidence512[y0 * inputSize + x1]) * fx
                    let bottom = Float(confidence512[y1 * inputSize + x0]) * (1 - fx) +
                        Float(confidence512[y1 * inputSize + x1]) * fx
                    let interpolated = UInt8(
                        min(255, max(0, Int((top * (1 - fy) + bottom * fy).rounded())))
                    )
                    bytes.set(index: Int32(y * outputWidth + x), value: Int8(bitPattern: interpolated))
                }
            }
            return EdgeMap(
                width: Int32(outputWidth),
                height: Int32(outputHeight),
                strength: bytes
            )
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
        self.sessionOptions = options
        self.session = session
        return session
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data)
            .map { String(format: "%02x", $0) }
            .joined()
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
           size.int64Value > 40_000_000,
           let cachedData = try? Data(contentsOf: target, options: .mappedIfSafe),
           sha256(cachedData).caseInsensitiveCompare(expectedSHA256) == .orderedSame {
            return target
        }
        try? FileManager.default.removeItem(at: target)

        let staging = directory.appendingPathComponent(modelName + ".download")
        try? FileManager.default.removeItem(at: staging)
        let state = DownloadState()
        let semaphore = DispatchSemaphore(value: 0)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 180
        configuration.timeoutIntervalForResource = 300
        let downloadSession = URLSession(configuration: configuration)
        downloadSession.downloadTask(with: modelURL) { temporaryURL, response, error in
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
        downloadSession.finishTasksAndInvalidate()
        if let error = state.error { throw error }
        guard FileManager.default.fileExists(atPath: staging.path) else {
            throw DexiNedDownloadError.missingDownload
        }

        let fileData = try Data(contentsOf: staging, options: .mappedIfSafe)
        let checksum = sha256(fileData)
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
