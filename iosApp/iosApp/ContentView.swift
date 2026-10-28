import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        NeuralEdgeInferenceProviderRegistry.shared.provider = DexiNedEdgeProvider.shared
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        // Let Compose handle system-bar insets through Scaffold to avoid a duplicate top gap.
        ComposeView()
            .ignoresSafeArea()
    }
}
