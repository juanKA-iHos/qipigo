package pe.ihos.qipigo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Integer> signals = new HashMap<>();
    private final Map<Integer, Long> seen = new HashMap<>(), shown = new HashMap<>();
    private BluetoothLeScanner scanner;
    private boolean scanning = false, foreground = false;
    private AlertDialog modal;
    private TextView status, devices, counts;
    private Button startButton;
    private JSONArray events = new JSONArray();
    private String storageError = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!scanning) return;
            long now = SystemClock.elapsedRealtime();
            for (int minor : new int[]{1, 2}) {
                if (now - seen.getOrDefault(minor, 0L) > 5000) signals.remove(minor);
            }
            refresh();
            if (modal == null && foreground) {
                int selected = 0, best = -1000;
                for (int minor : new int[]{1, 2}) {
                    Integer rssi = signals.get(minor);
                    if (rssi != null && rssi >= -75 && rssi > best &&
                        (!shown.containsKey(minor) || now - shown.get(minor) >= 60000)) {
                        selected = minor; best = rssi;
                    }
                }
                if (selected != 0) showCampaign(selected);
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) { consume(result); }
        @Override public void onBatchScanResults(List<ScanResult> results) { for (ScanResult r : results) consume(r); }
        @Override public void onScanFailed(int code) {
            runOnUiThread(() -> { stop(); status.setText("Error de escaneo BLE: " + code + ". Espera antes de reintentar."); });
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        readEvents();
        ScrollView scroll = new ScrollView(this);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content); setContentView(scroll);
        TextView title = text(content, "QipiGo · Piloto Android", 28);
        title.setTextColor(Color.rgb(0, 95, 180));
        text(content, "Campañas cerca de ti", 20);
        status = text(content, "Activa Bluetooth y Localización para comenzar.", 16);
        startButton = button(content, "Iniciar detección", () -> { if (scanning) stop(); else requestStart(); });
        button(content, "Ajustes de permisos", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        devices = text(content, "", 18);
        counts = text(content, "", 16);
        button(content, "Exportar historial JSON", () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "qipigo-interacciones.json"); startActivityForResult(intent, 20);
        });
        text(content, "Prueba con la app abierta. Se detiene al salir o bloquear la pantalla; pulsa Iniciar al volver.\n\nUmbral: −75 dBm. Espera por campaña: 60 segundos. El RSSI no representa metros exactos.\n\nCampañas incluidas sin internet; historial solo en este teléfono. Sin backend, sincronización ni captura GPS.", 15);
        text(content, "UUID: " + BeaconFrame.PILOT_UUID, 13);
        refresh();
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, 12, 0, 12); parent.addView(v); return v;
    }
    private Button button(LinearLayout parent, String value, Runnable action) {
        Button v = new Button(this); v.setText(value); parent.addView(v); v.setOnClickListener(view -> action.run()); return v;
    }
    private String[] required() {
        if (Build.VERSION.SDK_INT >= 31) return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    }
    private void requestStart() {
        ArrayList<String> missing = new ArrayList<>();
        for (String p : required()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) { requestPermissions(required(), 10); return; }
        begin();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 10) {
            for (String p : required()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                status.setText("Permisos pendientes. Autoriza Dispositivos cercanos y Ubicación precisa mientras usas la app desde Ajustes."); return;
            }
            begin();
        }
    }
    private void begin() {
        if (!foreground || scanning) return;
        try {
            BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
            if (adapter == null) { status.setText("Este teléfono no ofrece Bluetooth compatible."); return; }
            if (!adapter.isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 11); return;
            }
            LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
            boolean locationOn = Build.VERSION.SDK_INT >= 28 ? lm.isLocationEnabled() :
                (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            if (!locationOn) { status.setText("Activa Localización en el teléfono y pulsa Iniciar otra vez."); startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { status.setText("Escáner no disponible. Revisa Bluetooth."); return; }
            ScanFilter filter = new ScanFilter.Builder().setManufacturerData(0x004c, new byte[]{0x02, 0x15}).build();
            scanning = true;
            scanner.startScan(Collections.singletonList(filter), new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback);
            startButton.setText("Detener detección"); status.setText("Buscando PuntoGo · mantén abierta la app");
            handler.post(tick);
        } catch (SecurityException | IllegalStateException e) {
            stop(); status.setText("No se pudo iniciar: " + e.getMessage());
        }
    }
    private void consume(ScanResult result) {
        if (result.getScanRecord() == null) return;
        BeaconFrame frame = BeaconFrame.parse(result.getScanRecord().getManufacturerSpecificData(0x004c));
        if (frame == null || !frame.isPilot() || result.getRssi() == 0) return;
        runOnUiThread(() -> {
            if (!scanning || !foreground) return;
            signals.put(frame.minor, result.getRssi()); seen.put(frame.minor, SystemClock.elapsedRealtime());
        });
    }
    private void showCampaign(int minor) {
        modal = new AlertDialog.Builder(this)
            .setTitle(minor == 1 ? "Bienvenido a PuntoGo 001" : "Descubre PuntoGo 002")
            .setMessage((minor == 1 ? "K8 · 398745" : "K5 · 508522") + "\n\nCampaña de prueba QipiGo. Tu próxima oportunidad está cerca.\n\nContenido de demostración; no realiza compras ni abre enlaces.")
            .setPositiveButton("Me interesa", (dialog, which) -> record(minor, "clic"))
            .setNegativeButton("Cerrar", null).create();
        modal.setOnDismissListener(dialog -> modal = null);
        modal.show();
        shown.put(minor, SystemClock.elapsedRealtime()); record(minor, "vista");
    }
    private void refresh() {
        if (devices == null) return;
        StringBuilder b = new StringBuilder();
        for (int minor : new int[]{1, 2}) b.append(minor == 1 ? "K8 · 398745" : "K5 · 508522")
            .append(" · Major 1 / Minor ").append(minor).append("\n")
            .append(signals.containsKey(minor) ? signals.get(minor) + " dBm" : "Sin detección reciente").append("\n\n");
        devices.setText(b.toString());
        int views = 0, clicks = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null) continue;
            if (e.optString("type").equals("vista")) views++;
            if (e.optString("type").equals("clic")) clicks++;
        }
        counts.setText("Vistas: " + views + " · Clics: " + clicks + "\n" + storageError);
    }
    private File eventFile() { return new File(getFilesDir(), "events.json"); }
    private void readEvents() {
        if (!eventFile().exists()) return;
        try (InputStream input = new FileInputStream(eventFile()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            events = new JSONArray(out.toString("UTF-8"));
        } catch (Exception e) { storageError = "No se pudo leer el historial. El archivo anterior se conserva; no se guardarán eventos nuevos."; }
    }
    private void record(int minor, String type) {
        try {
            events.put(new JSONObject().put("id", UUID.randomUUID().toString())
                .put("date", java.time.Instant.now().toString()).put("uuid", BeaconFrame.PILOT_UUID.toString())
                .put("major", 1).put("minor", minor).put("type", type));
            // Avoid replacing a file that failed to decode.
            if (!storageError.startsWith("No se pudo leer")) {
                android.util.AtomicFile atomic = new android.util.AtomicFile(eventFile());
                FileOutputStream stream = null;
                try {
                    stream = atomic.startWrite(); stream.write(events.toString(2).getBytes(StandardCharsets.UTF_8));
                    atomic.finishWrite(stream); storageError = "";
                } catch (Exception e) { if (stream != null) atomic.failWrite(stream); storageError = "Eventos en memoria: no se pudo guardar el historial."; }
            }
        } catch (JSONException e) { storageError = "No se pudo registrar el evento."; }
        refresh();
    }
    private void stop() {
        scanning = false; handler.removeCallbacks(tick);
        if (scanner != null) try { scanner.stopScan(callback); } catch (SecurityException | IllegalStateException ignored) { }
        scanner = null; signals.clear(); seen.clear();
        if (modal != null) modal.dismiss();
        if (startButton != null) { startButton.setText("Iniciar detección"); status.setText("Detección detenida."); refresh(); }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; }
    @Override protected void onPause() { foreground = false; stop(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 11 && result == RESULT_OK) status.setText("Bluetooth activado. Pulsa Iniciar detección.");
        if (request == 20 && result == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("Archivo no disponible");
                out.write(events.toString(2).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Historial exportado", Toast.LENGTH_LONG).show();
            } catch (Exception e) { status.setText("No se pudo exportar: " + e.getMessage()); }
        }
    }
}
