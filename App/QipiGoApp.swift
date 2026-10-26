import SwiftUI
import CoreLocation
import UniformTypeIdentifiers

struct Interaction: Codable, Identifiable {
    let id: UUID
    let date: Date
    let minor: Int
    let type: String
}

struct Campaign: Identifiable {
    let id: Int
    let title: String
    let message: String
    let device: String
    static let all = [
        Campaign(id: 1, title: "Bienvenido a PuntoGo 001", message: "Campaña de prueba del K8. Tu próxima oportunidad está cerca.", device: "K8 · 398745"),
        Campaign(id: 2, title: "Descubre PuntoGo 002", message: "Campaña de prueba del K5. Una baliza diferente, una experiencia diferente.", device: "K5 · 508522")
    ]
}

final class Pilot: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let beaconUUID = UUID(uuidString: "7777772E-6B6B-6D63-6E2E-636F6D000001")!
    private let manager = CLLocationManager()
    private let constraint = CLBeaconIdentityConstraint(uuid: beaconUUID, major: 1)
    private var requested = false
    private var lastShown: [Int: Date] = [:]
    private var lastSeen: [Int: Date] = [:]
    private var timer: Timer?
    @Published var running = false
    @Published var status = "Listo para iniciar. Activa Bluetooth y Localización."
    @Published var signals: [Int: Int] = [:]
    @Published var campaign: Campaign?
    @Published var events: [Interaction] = []
    @Published var storageError: String?
    private var file: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("qipigo-events.json")
    }
    override init() {
        super.init()
        manager.delegate = self
        do {
            if FileManager.default.fileExists(atPath: file.path) {
                events = try JSONDecoder().decode([Interaction].self, from: Data(contentsOf: file))
            }
        } catch { storageError = "No se pudo leer el historial: \(error.localizedDescription)" }
    }
    func start() {
        requested = true
        switch manager.authorizationStatus {
        case .notDetermined:
            status = "Autoriza Localización mientras usas QipiGo."
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            guard CLLocationManager.isRangingAvailable() else {
                status = "Este equipo no permite medir balizas."
                return
            }
            guard !running else { return }
            running = true
            status = "Buscando los dos PuntoGo · app abierta"
            manager.startRangingBeacons(satisfying: constraint)
            timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.expire() }
        case .denied, .restricted:
            status = "Localización no autorizada. Revísala en Ajustes."
        @unknown default: status = "Estado de permiso desconocido."
        }
    }
    func stop() {
        requested = false
        manager.stopRangingBeacons(satisfying: constraint)
        timer?.invalidate()
        timer = nil
        running = false
        signals = [:]
        lastSeen = [:]
        campaign = nil
        status = "Detección detenida."
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if requested {
            if manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted { stop() }
            start()
        }
    }
    func locationManager(_ manager: CLLocationManager, didRange beacons: [CLBeacon], satisfying beaconConstraint: CLBeaconIdentityConstraint) {
        guard running else { return }
        let known = beacons.filter { $0.uuid == Self.beaconUUID && $0.major.intValue == 1 && [1, 2].contains($0.minor.intValue) && $0.rssi != 0 }
        for beacon in known {
            signals[beacon.minor.intValue] = beacon.rssi
            lastSeen[beacon.minor.intValue] = Date()
        }
        expire()
        guard campaign == nil else { return }
        // RSSI is a radio threshold for the pilot, never an exact distance.
        let eligible = known.filter { beacon in
            beacon.rssi >= -75 && Date().timeIntervalSince(lastShown[beacon.minor.intValue] ?? .distantPast) >= 60
        }
        if let strongest = eligible.max(by: { $0.rssi < $1.rssi }), let match = Campaign.all.first(where: { $0.id == strongest.minor.intValue }) {
            campaign = match
        }
    }
    private func expire() {
        let stale = lastSeen.filter { Date().timeIntervalSince($0.value) > 5 }.map(\.key)
        for id in stale { lastSeen.removeValue(forKey: id); signals.removeValue(forKey: id) }
    }
    func locationManager(_ manager: CLLocationManager, didFailRangingFor beaconConstraint: CLBeaconIdentityConstraint, error: Error) {
        stop()
        status = "Error de detección: \(error.localizedDescription)"
    }
    func impression(_ minor: Int) {
        guard Date().timeIntervalSince(lastShown[minor] ?? .distantPast) >= 60 else { return }
        lastShown[minor] = Date()
        record(minor, "vista")
    }
    func record(_ minor: Int, _ type: String) {
        events.append(Interaction(id: UUID(), date: Date(), minor: minor, type: type))
        do { try JSONEncoder().encode(events).write(to: file, options: .atomic); storageError = nil }
        catch { storageError = "Evento en memoria; no se pudo guardar: \(error.localizedDescription)" }
    }
}

struct EventDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(events: [Interaction]) {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        data = (try? encoder.encode(events)) ?? Data("[]".utf8)
    }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

@main
struct QipiGoApp: App {
    @StateObject private var pilot = Pilot()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            PilotView(pilot: pilot)
                .onChange(of: scenePhase) { phase in
                    if phase == .background { pilot.stop() }
                }
        }
    }
}

struct PilotView: View {
    @ObservedObject var pilot: Pilot
    @State private var exporting = false
    @State private var exportError: String?
    var body: some View {
        NavigationView {
            List {
                Section {
                    Label("QipiGo · Piloto BLE", systemImage: "dot.radiowaves.left.and.right")
                        .font(.title2.bold()).foregroundColor(.blue)
                    Text("Campañas que aparecen cuando estás cerca.")
                    Text(pilot.status).font(.callout).foregroundColor(.secondary)
                    Button(pilot.running ? "Detener detección" : "Iniciar detección") {
                        if pilot.running { pilot.stop() } else { pilot.start() }
                    }.buttonStyle(.borderedProminent)
                    Button("Abrir ajustes de permisos") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }
                Section("Tus PuntoGo") {
                    ForEach(Campaign.all) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(item.device).font(.headline)
                            Text("Major 1 · Minor \(item.id)")
                            Text(pilot.signals[item.id].map { "Señal recibida: \($0) dBm" } ?? "Sin detección reciente")
                                .foregroundColor(.secondary)
                        }.padding(.vertical, 4)
                    }
                    Text("UUID: \(Pilot.beaconUUID.uuidString)").font(.caption).textSelection(.enabled)
                }
                Section("Interacciones locales") {
                    Text("Vistas: \(pilot.events.filter { $0.type == "vista" }.count) · Clics: \(pilot.events.filter { $0.type == "clic" }.count)")
                    Button("Exportar historial JSON") { exporting = true }
                    ForEach(pilot.events.suffix(10).reversed()) { event in
                        Text("\(event.type.capitalized) · PuntoGo 00\(event.minor) · \(event.date.formatted(date: .omitted, time: .standard))")
                            .font(.caption)
                    }
                    if let error = pilot.storageError ?? exportError { Text(error).foregroundColor(.red) }
                }
                Section("Alcance de esta prueba") {
                    Text("Detección real con la app abierta. Al pasar a segundo plano se detiene; pulsa Iniciar al volver. Sin backend ni sincronización todavía.")
                    Text("Activación desde −75 dBm; espera de 60 segundos por campaña. La señal no equivale a una distancia exacta. Solo se guardan eventos locales, sin GPS ni datos personales.")
                }
            }
            .navigationTitle("QipiGo")
            .sheet(item: $pilot.campaign) { campaign in
                VStack(spacing: 24) {
                    Image(systemName: "gift.fill").font(.system(size: 64)).foregroundColor(.blue)
                    Text(campaign.title).font(.title.bold())
                    Text(campaign.message).multilineTextAlignment(.center)
                    Text(campaign.device).foregroundColor(.secondary)
                    Button("Me interesa · registrar clic") {
                        pilot.record(campaign.id, "clic")
                        pilot.campaign = nil
                    }.buttonStyle(.borderedProminent)
                    Button("Cerrar") { pilot.campaign = nil }
                    Text("Contenido de demostración; no realiza compras ni abre enlaces.").font(.caption)
                }.padding(32)
                .onAppear { pilot.impression(campaign.id) }
            }
            .fileExporter(isPresented: $exporting, document: EventDocument(events: pilot.events), contentType: .json, defaultFilename: "qipigo-interacciones") { result in
                if case .failure(let error) = result { exportError = error.localizedDescription }
            }
        }.navigationViewStyle(.stack)
    }
}
