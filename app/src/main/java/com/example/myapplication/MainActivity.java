package com.example.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone prototype. All trip information is sample data. */
public class MainActivity extends Activity {
    private TextView status;
    private boolean takingBreak;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection activeConnection;
    private TextView apiResult;
    private Button apiButton;
    private static final int INK = Color.rgb(235, 242, 249);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        takingBreak = state != null && state.getBoolean("takingBreak");
        getWindow().setStatusBarColor(Color.rgb(13, 23, 36));
        getWindow().setNavigationBarColor(Color.rgb(13, 23, 36));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 23, 36));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(24), dp(32), dp(24));
        scroll.addView(content);
        content.addView(label("DRIVE COMPANION", 16, Color.rgb(101, 224, 192)));
        content.addView(label("여유로운 이동의 시작", 32, INK));
        content.addView(label("차량 앱 첫 번째 프로토타입 · 모든 정보는 예시입니다", 16, Color.LTGRAY));
        Button diagnostics = button("차량 · SDK 상태 테스트 열기");
        diagnostics.setId(com.example.myapplication.R.id.open_diagnostics);
        content.addView(diagnostics);
        diagnostics.setOnClickListener(v -> startActivity(new android.content.Intent(this, DiagnosticsActivity.class)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(27, 42, 59));
        background.setCornerRadius(dp(20));
        card.setBackground(background);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(24);
        content.addView(card, cardParams);
        card.addView(label("오늘의 이동", 18, Color.rgb(101, 224, 192)));
        card.addView(label("서울숲으로 떠나는 짧은 여행", 26, INK));
        card.addView(label("예상 소요 25분  ·  거리 12.4 km", 18, Color.LTGRAY));
        status = label("", 20, INK);
        card.addView(status);
        updateStatus();

        Button destination = button("목적지 정보 보기");
        content.addView(destination);
        destination.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("서울숲 · 예시 목적지")
                .setMessage("서울특별시 성동구 뚝섬로 273\n\n목적지 정보 화면을 확인하기 위한 데모입니다. 실제 길 안내는 연결되어 있지 않습니다.")
                .setPositiveButton("확인", null).show());
        Button rest = button("휴식 모드 전환");
        content.addView(rest);
        rest.setOnClickListener(v -> {
            takingBreak = !takingBreak;
            updateStatus();
        });
        apiButton = button("테스트 API 호출");
        content.addView(apiButton);
        apiResult = label(state == null ? "버튼을 눌러 서버에서 예시 게시글을 가져오세요."
                : state.getString("apiResult", "버튼을 눌러 서버에서 예시 게시글을 가져오세요."), 18, INK);
        apiResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(apiResult);
        content.addView(label("출처: JSONPlaceholder · GET /posts/1 · 실제 인터넷 요청", 14, Color.LTGRAY));
        apiButton.setOnClickListener(v -> fetchSamplePost());
        content.addView(label("데모 전용 · 실제 차량 정보 및 내비게이션 연동 없음", 14, Color.LTGRAY));
        setContentView(scroll);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(32) + insets.getSystemWindowInsetLeft(),
                    dp(24) + insets.getSystemWindowInsetTop(),
                    dp(32) + insets.getSystemWindowInsetRight(),
                    dp(24) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        content.requestApplyInsets();
    }

    private void updateStatus() {
        status.setText(takingBreak ? "휴식 모드 · 잠시 쉬어가세요" : "출발 준비 · 즐거운 이동 되세요");
        status.announceForAccessibility(status.getText());
    }

    private void fetchSamplePost() {
        apiButton.setEnabled(false);
        apiButton.setText("불러오는 중…");
        apiResult.setText("서버에 연결하고 있습니다…");
        network.execute(() -> {
            HttpURLConnection connection = null;
            String result;
            try {
                connection = (HttpURLConnection) new URL("https://jsonplaceholder.typicode.com/posts/1").openConnection();
                activeConnection = connection;
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("HTTP " + code);
                }
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                        if (body.length() > 65536) throw new java.io.IOException("응답 크기 초과");
                    }
                }
                JSONObject post = new JSONObject(body.toString());
                result = "연결 성공 · HTTP " + code + " · 게시글 #" + post.getInt("id")
                        + "\n\n" + post.getString("title") + "\n\n" + post.getString("body");
            } catch (java.net.SocketTimeoutException e) {
                result = "응답 시간이 초과되었습니다. 잠시 후 다시 눌러주세요.";
            } catch (Exception e) {
                result = "불러오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러주세요.";
                android.util.Log.w("SampleApi", "Sample post request failed", e);
            } finally {
                if (connection != null) connection.disconnect();
                activeConnection = null;
            }
            final String message = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                apiResult.setText(message);
                apiButton.setText("테스트 API 다시 호출");
                apiButton.setEnabled(true);
            });
        });
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("takingBreak", takingBreak);
        if (apiButton.isEnabled()) outState.putString("apiResult", apiResult.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(8));
        if (size >= 26) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String title) {
        Button view = new Button(this);
        view.setText(title);
        view.setTextSize(18);
        view.setAllCaps(false);
        view.setMinHeight(dp(64));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
