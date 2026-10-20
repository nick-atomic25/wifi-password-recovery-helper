package com.example.wifirecovery;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int REQ_WIFI = 501;
    private static final String KEY_ALIAS = "WiFiRecoveryCredentialKey";
    private final SecureRandom random = new SecureRandom();
    private WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;
    private boolean receiverRegistered = false;
    private Spinner networkSpinner, brandSpinner;
    private ArrayAdapter<String> networkAdapter;
    private final List<String> ssids = new ArrayList<>();
    private final Map<String, ScanResult> scanBySsid = new HashMap<>();
    private EditText prefixInput, savePasswordInput, testPasswordInput;
    private TextView testStatus;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback activeNetworkCallback;
    private String pendingTestSsid, pendingTestPassword;
    private CheckBox lower, upper, digits, symbols;
    private TextView candidateOutput, generatedCount, timerText, networkInfo, savedStatus;
    private long generated = 0, startedAt = 0;
    private final List<String> history = new ArrayList<>();
    private SharedPreferences prefs;

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.rgb(28,40,52));
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(12),dp(18),dp(24)); return l; }
    private void add(LinearLayout p, View v, int bottom) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(bottom); p.addView(v,lp); }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(23,105,224)); bg.setCornerRadius(dp(12));
        b.setBackground(bg); b.setPadding(dp(14),dp(10),dp(14),dp(10));
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        prefs = getSharedPreferences("credential_vault", MODE_PRIVATE);
        scanReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) populateNetworksFromResults();
            }
        };
        try { registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)); receiverRegistered = true; }
        catch (Exception ignored) { }
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = column(); page.setBackgroundColor(Color.rgb(246,248,251));
        TextView title = text("📶  Wi-Fi Recovery",26,true); title.setTextColor(Color.rgb(18,59,93)); add(page,title,6);
        add(page,text("Multi-brand recovery assistance for networks you own or are authorized to manage.",14,false),14);
        TextView notice = text("This app can scan nearby Wi-Fi names, generate local candidate strings, and securely save credentials. On Android 10+, you can initiate a single WPA2-Personal candidate verification through Android's Wi-Fi connection flow; system approval may be required. The app does not run an unlimited automated guessing loop.",14,false);
        notice.setPadding(dp(13),dp(13),dp(13),dp(13)); notice.setBackgroundColor(Color.rgb(231,240,248)); add(page,notice,18);

        add(page,text("①  Nearby Wi-Fi networks",18,true),6);
        networkSpinner = new Spinner(this); networkAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>()); networkSpinner.setAdapter(networkAdapter); add(page,networkSpinner,8);
        Button scanButton = button("↻  Scan / refresh networks"); add(page,scanButton,6);
        networkInfo = text("Network details will appear here.",13,false); add(page,networkInfo,16);
        scanButton.setOnClickListener(v -> scanNetworks());
        networkSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateNetworkInfo(); loadSavedPasswordForSelectedNetwork(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        add(page,text("②  Router brand detection",18,true),6);
        String[] brands = {"Auto-detect / unknown", "TP-Link", "Huawei", "ZTE", "PLDT Home WiFi", "Globe At Home", "ASUS", "D-Link", "Tenda", "MikroTik", "Other"};
        brandSpinner = new Spinner(this); brandSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, brands)); add(page,brandSpinner,8);
        Button guide = button("Show recovery guidance"); add(page,guide,8);
        TextView guideText = text("Use a connected device's Wi-Fi sharing feature, the router label, ISP paperwork, or the router's authorized administration page.",14,false); add(page,guideText,16);
        guide.setOnClickListener(v -> guideText.setText(guidance(String.valueOf(brandSpinner.getSelectedItem()))));

        add(page,text("③  Automatic candidate generator",18,true),6);
        add(page,text("Candidate length is selected automatically for each generated WPA-Personal candidate (8–63 characters). Generation is local; it does not prove that a candidate is correct.",13,false),8);
        lower = new CheckBox(this); lower.setText("Lowercase (a–z)"); lower.setChecked(true); add(page,lower,0);
        upper = new CheckBox(this); upper.setText("Uppercase (A–Z)"); upper.setChecked(true); add(page,upper,0);
        digits = new CheckBox(this); digits.setText("Numbers (0–9)"); digits.setChecked(true); add(page,digits,0);
        symbols = new CheckBox(this); symbols.setText("Special characters"); symbols.setChecked(true); add(page,symbols,6);
        prefixInput = new EditText(this); prefixInput.setSingleLine(true); prefixInput.setHint("Optional known prefix"); add(page,prefixInput,8);
        Button gen = button("✦  Generate candidate automatically"); add(page,gen,8);
        candidateOutput = text("Candidate appears here.",18,true); candidateOutput.setGravity(Gravity.CENTER); candidateOutput.setTextIsSelectable(true); candidateOutput.setPadding(dp(12),dp(18),dp(12),dp(18)); candidateOutput.setBackgroundColor(Color.WHITE); add(page,candidateOutput,8);
        generatedCount = text("Candidates generated: 0",14,false); add(page,generatedCount,4);
        timerText = text("Elapsed generation time: 0 sec · ETA for router recovery: unavailable",13,false); add(page,timerText,16);
        gen.setOnClickListener(v -> generateCandidate());

        add(page,text("④  Verify selected candidate",18,true),6);
        add(page,text("Enter a candidate you want to verify. Android 10+ may display a system connection approval prompt. This does not run a password-guessing loop; each attempt must be initiated by you.",13,false),8);
        testPasswordInput = new EditText(this); testPasswordInput.setSingleLine(true); testPasswordInput.setHint("Candidate password (WPA2-Personal)"); testPasswordInput.setInputType(129); add(page,testPasswordInput,8);
        Button testButton = button("✓  Verify candidate with Android"); add(page,testButton,6);
        Button cancelTestButton = button("■  Cancel / release connection"); add(page,cancelTestButton,6);
        testStatus = text("No candidate verification attempted.",13,false); add(page,testStatus,16);
        testButton.setOnClickListener(v -> verifySingleCandidate());
        cancelTestButton.setOnClickListener(v -> cancelCandidateVerification("Verification cancelled by user."));

        add(page,text("⑤  Encrypted password vault",18,true),6);
        add(page,text("Only save a password you already know or have legitimately recovered. The app encrypts it using an Android Keystore key and stores it locally on this device.",13,false),8);
        savePasswordInput = new EditText(this); savePasswordInput.setSingleLine(true); savePasswordInput.setHint("Recovered Wi-Fi password"); savePasswordInput.setInputType(129); add(page,savePasswordInput,8);
        Button saveButton = button("🔒  Encrypt and save password"); add(page,saveButton,8);
        savedStatus = text("No saved credential loaded.",13,false); add(page,savedStatus,8);
        Button showButton = button("◉  Show saved password"); add(page,showButton,8);
        Button clearButton = button("⌫  Delete saved password"); add(page,clearButton,8);
        saveButton.setOnClickListener(v -> saveCredential());
        showButton.setOnClickListener(v -> revealCredential());
        clearButton.setOnClickListener(v -> deleteCredential());

        add(page,text("Recovery checklist",18,true),6);
        add(page,text("• On Android, open Settings → Wi-Fi → current network → Share. Device unlock may be required.\n\n• Check the router label and ISP paperwork.\n\n• If you can sign in to the router admin page, inspect Wireless / Wi-Fi settings.\n\n• Contact the ISP before factory-resetting a supplied router; a reset may erase provider configuration.",14,false),8);
        scroll.addView(page); setContentView(scroll);
        requestWifiPermissionsAndScan();
    }

    private void requestWifiPermissionsAndScan() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) needed.add("android.permission.NEARBY_WIFI_DEVICES");
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_WIFI); else scanNetworks();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WIFI) {
            boolean ok = true; for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) scanNetworks(); else networkInfo.setText("Wi-Fi scan permission denied. You can still use recovery guidance, candidate generation, and local password storage.");
        }
    }
    private void scanNetworks() {
        try {
            if (wifiManager == null) { networkInfo.setText("Wi-Fi service is unavailable on this device."); return; }
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { requestWifiPermissionsAndScan(); return; }
            boolean started = wifiManager.startScan();
            if (!started) networkInfo.setText("Android throttled or declined a fresh scan. Showing the latest available scan results; try again shortly.");
            populateNetworksFromResults();
        } catch (SecurityException e) {
            networkInfo.setText("Android denied Wi-Fi scan access. Check app permissions and turn on Location if your Android version requires it.");
        }
    }
    private void populateNetworksFromResults() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        scanBySsid.clear();
        try {
            List<ScanResult> results = wifiManager == null ? null : wifiManager.getScanResults();
            if (results != null) for (ScanResult result : results) if (result.SSID != null && !result.SSID.trim().isEmpty()) {
                unique.add(result.SSID);
                ScanResult old = scanBySsid.get(result.SSID);
                if (old == null || result.level > old.level) scanBySsid.put(result.SSID, result);
            }
            try { String current = wifiManager.getConnectionInfo().getSSID(); if (current != null && !current.equals("<unknown ssid>")) unique.add(current.replaceAll("^\"|\"$", "")); } catch (Exception ignored) { }
        } catch (SecurityException e) {
            networkInfo.setText("Android denied access to scan results. Check Wi-Fi and location permissions.");
        }
        String previous = selectedSsid();
        ssids.clear(); ssids.addAll(unique);
        networkAdapter.clear();
        if (ssids.isEmpty()) networkAdapter.add("No networks found — tap scan again"); else networkAdapter.addAll(ssids);
        networkAdapter.notifyDataSetChanged();
        if (!previous.isEmpty() && ssids.contains(previous)) networkSpinner.setSelection(ssids.indexOf(previous));
        updateNetworkInfo();
    }
    private String selectedSsid() {
        Object item = networkSpinner == null ? null : networkSpinner.getSelectedItem();
        if (item == null) return "";
        String s = item.toString(); return s.startsWith("No networks found") ? "" : s;
    }
    private void updateNetworkInfo() {
        String ssid = selectedSsid();
        if (ssid.isEmpty()) { networkInfo.setText("No SSID selected. Enable Wi-Fi, grant scan permission, and refresh."); return; }
        ScanResult result = scanBySsid.get(ssid);
        String details = "Selected: " + ssid;
        if (result != null) details += "\nSignal: " + result.level + " dBm\nSecurity: " + securityLabel(result.capabilities);
        details += "\nBrand estimate: " + inferBrand(ssid) + "\nBrand identification is best-effort; SSID names are not reliable proof of manufacturer.";
        networkInfo.setText(details);
        if (brandSpinner != null) {
            String guess = inferBrand(ssid);
            String[] brands = {"Auto-detect / unknown", "TP-Link", "Huawei", "ZTE", "PLDT Home WiFi", "Globe At Home", "ASUS", "D-Link", "Tenda", "MikroTik", "Other"};
            int index = 0; for (int i=1;i<brands.length;i++) if (brands[i].equals(guess)) { index=i; break; }
            brandSpinner.setSelection(index);
        }
    }
    private String securityLabel(String caps) {
        if (caps == null) return "Unknown";
        String c = caps.toUpperCase(Locale.US);
        if (c.contains("WPA3")) return "WPA3 (candidate test may not support this mode)";
        if (c.contains("WPA2") && c.contains("WPA")) return "WPA2/WPA-Personal (check router mode)";
        if (c.contains("WPA2")) return "WPA2";
        if (c.contains("WPA")) return "WPA/WPA-Personal";
        if (c.contains("WEP")) return "WEP (not supported by candidate test)";
        return "Open / security not reported";
    }
    private String inferBrand(String ssid) {
        String n = ssid == null ? "" : ssid.toUpperCase(Locale.US).replace("_", " ").replace("-", " ");
        if (n.contains("PLDT") || n.contains("HOME WIFI")) return "PLDT Home WiFi";
        if (n.contains("GLOBE") || n.contains("GFIBER")) return "Globe At Home";
        if (n.contains("HUAWEI") || n.contains("HUAWEI WIFI")) return "Huawei";
        if (n.contains("ZTE") || n.contains("FIBERHOME")) return "ZTE";
        if (n.contains("TP LINK") || n.contains("TPLINK")) return "TP-Link";
        if (n.contains("ASUS")) return "ASUS";
        if (n.contains("D LINK") || n.contains("DLINK")) return "D-Link";
        if (n.contains("TENDA")) return "Tenda";
        if (n.contains("MIKROTIK")) return "MikroTik";
        return "Auto-detect / unknown";
    }
    private void generateCandidate() {
        int length = 8 + random.nextInt(56);
        StringBuilder pool=new StringBuilder();
        if(lower.isChecked()) pool.append("abcdefghijklmnopqrstuvwxyz");
        if(upper.isChecked()) pool.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        if(digits.isChecked()) pool.append("0123456789");
        if(symbols.isChecked()) pool.append("!@#$%^&*()-_=+[]{};:,.?/\\|~");
        if(pool.length()==0) { candidateOutput.setText("Select at least one character set."); return; }
        String prefix=prefixInput.getText().toString();
        if (prefix.length() > 63) { candidateOutput.setText("Known prefix cannot exceed 63 characters for this WPA-Personal candidate."); return; }
        length = Math.max(length, prefix.length());
        if (startedAt == 0) startedAt = SystemClock.elapsedRealtime();
        StringBuilder candidate=new StringBuilder(prefix); while(candidate.length()<length) candidate.append(pool.charAt(random.nextInt(pool.length())));
        generated++; history.add(candidate.toString()); if(history.size()>100) history.remove(0);
        candidateOutput.setText(candidate.toString() + "\n\nLength: " + candidate.length() + " characters");
        if (testPasswordInput != null) testPasswordInput.setText(candidate.toString());
        generatedCount.setText(String.format(Locale.US,"Candidates generated: %,d",generated));
        long elapsed = Math.max(0,(SystemClock.elapsedRealtime()-startedAt)/1000);
        timerText.setText(String.format(Locale.US,"Elapsed generation time: %d sec · Generation rate: %.1f candidates/sec · Router recovery ETA: unavailable",elapsed, elapsed == 0 ? (double)generated : (double)generated/elapsed));
    }
    private String guidance(String brand) {
        return "Recovery guidance for " + brand + ":\n\n1. Check the router label and ISP paperwork for the default Wi-Fi credentials.\n2. If a phone or computer is already connected, use its supported saved-network sharing or password-view feature.\n3. If you have administrator access, open the router's official local management page and inspect Wi-Fi / Wireless settings.\n4. Contact the ISP for managed gateways.\n\nExact menus vary by model and firmware. Candidate verification is one-at-a-time and user-initiated; this app does not run an automated password-guessing loop.";
    }
    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return kg.generateKey();
    }
    private String prefKey(String ssid) { return "credential_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ssid.getBytes(StandardCharsets.UTF_8)); }
    private void saveCredential() {
        String ssid = selectedSsid(), password = savePasswordInput.getText().toString();
        if (ssid.isEmpty()) { savedStatus.setText("Select a Wi-Fi network first."); return; }
        if (password.isEmpty()) { savedStatus.setText("Enter a recovered password first."); return; }
        storeCredential(ssid, password);
        savePasswordInput.setText("");
    }
    private boolean storeCredential(String ssid, String password) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV(); byte[] packed = new byte[iv.length + encrypted.length]; System.arraycopy(iv,0,packed,0,iv.length); System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);
            prefs.edit().putString(prefKey(ssid), Base64.getEncoder().encodeToString(packed)).apply();
            if (savedStatus != null) savedStatus.setText("Encrypted password saved locally for: " + ssid);
            return true;
        } catch (Exception e) {
            if (savedStatus != null) savedStatus.setText("Could not encrypt/save credential on this device.");
            return false;
        }
    }
    private void verifySingleCandidate() {
        String ssid = selectedSsid();
        String password = testPasswordInput == null ? "" : testPasswordInput.getText().toString();
        if (ssid.isEmpty()) { testStatus.setText("Select a Wi-Fi network first."); return; }
        if (password.length() < 8 || password.length() > 63) { testStatus.setText("For this WPA2-Personal test, enter a passphrase from 8 to 63 characters."); return; }
        if (Build.VERSION.SDK_INT < 29) { testStatus.setText("This verification method requires Android 10 (API 29) or later. No attempt was made."); return; }
        if (activeNetworkCallback != null) { testStatus.setText("A verification request is already active. Cancel it before starting another."); return; }
        try {
            pendingTestSsid = ssid; pendingTestPassword = password;
            WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(password).build();
            NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(specifier).build();
            activeNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        if (activeNetworkCallback == null) return;
                        testStatus.setText("Android reports a connection to '" + pendingTestSsid + "'. Saving this candidate securely...");
                        boolean saved = storeCredential(pendingTestSsid, pendingTestPassword);
                        if (saved) testStatus.setText("Connection established for '" + pendingTestSsid + "'. Candidate saved in the encrypted vault. Keep this request active until you cancel it.");
                        pendingTestPassword = null;
                    });
                }
                @Override public void onUnavailable() {
                    runOnUiThread(() -> {
                        if (activeNetworkCallback != null) testStatus.setText("Android could not establish the requested connection. The candidate was not saved. This can indicate a wrong password, a network/security mismatch, a rejected prompt, or OS restrictions.");
                        releaseCandidateRequest();
                    });
                }
                @Override public void onLost(Network network) {
                    runOnUiThread(() -> { if (activeNetworkCallback != null) testStatus.setText("The test Wi-Fi connection was lost. The saved credential, if any, remains in the vault."); });
                }
            };
            testStatus.setText("Requesting a single connection attempt. Approve or reject the Android system prompt if shown...");
            connectivityManager.requestNetwork(request, activeNetworkCallback, 30000);
        } catch (Exception e) {
            releaseCandidateRequest();
            testStatus.setText("Could not start verification on this device. No password was saved.");
        }
    }
    private void cancelCandidateVerification(String message) {
        releaseCandidateRequest();
        if (testStatus != null) testStatus.setText(message);
    }
    private void releaseCandidateRequest() {
        if (activeNetworkCallback != null && connectivityManager != null) {
            try { connectivityManager.unregisterNetworkCallback(activeNetworkCallback); } catch (Exception ignored) { }
        }
        activeNetworkCallback = null; pendingTestSsid = null; pendingTestPassword = null;
    }
    @Override protected void onDestroy() {
        releaseCandidateRequest();
        if (receiverRegistered && scanReceiver != null) { try { unregisterReceiver(scanReceiver); } catch (Exception ignored) { } }
        super.onDestroy();
    }
    private String decryptCredential(String ssid) throws Exception {
        String stored = prefs.getString(prefKey(ssid), null); if (stored == null) return null;
        byte[] packed = Base64.getDecoder().decode(stored); if (packed.length < 13) throw new IllegalStateException("Invalid stored credential");
        byte[] iv = Arrays.copyOfRange(packed,0,12), encrypted = Arrays.copyOfRange(packed,12,packed.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
    }
    private void loadSavedPasswordForSelectedNetwork() {
        String ssid = selectedSsid(); if (ssid.isEmpty() || savedStatus == null) return;
        savedStatus.setText(prefs.contains(prefKey(ssid)) ? "An encrypted password is saved for: " + ssid : "No saved password for: " + ssid);
    }
    private void revealCredential() {
        String ssid = selectedSsid(); if (ssid.isEmpty()) { savedStatus.setText("Select a network first."); return; }
        try { String password = decryptCredential(ssid); if (password == null) savedStatus.setText("No saved password for: " + ssid); else { savePasswordInput.setText(password); savePasswordInput.setInputType(1); savedStatus.setText("Password decrypted locally for: " + ssid + " (shown in the field above)."); } }
        catch (Exception e) { savedStatus.setText("Unable to decrypt this saved password. The device's app data or Keystore key may have changed."); }
    }
    private void deleteCredential() {
        String ssid = selectedSsid(); if (ssid.isEmpty()) { savedStatus.setText("Select a network first."); return; }
        prefs.edit().remove(prefKey(ssid)).apply(); savedStatus.setText("Saved password deleted for: " + ssid); savePasswordInput.setText("");
    }
}
