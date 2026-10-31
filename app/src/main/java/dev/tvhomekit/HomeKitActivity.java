package dev.tvhomekit;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** Экран с кодом для добавления телевизора в приложение «Дом». */
public final class HomeKitActivity extends Activity {
    private static final int CATEGORY_TELEVISION = 31;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 2000);
        }
    };

    private Store store;
    private TextView title;
    private TextView hint;
    private TextView state;
    private ImageView qr;
    private TextView pin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HomeKitService.start(this);
        store = new Store(this);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        title = label(44, Color.WHITE, true);
        hint = label(24, 0xFFC7C7CC, false);
        hint.setPadding(0, 24, 0, 24);
        pin = label(56, 0xFFF5A623, true);
        state = label(20, 0xFF8E8E93, false);
        state.setPadding(0, 24, 0, 0);
        text.addView(title);
        text.addView(hint);
        text.addView(pin);
        text.addView(state);

        qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(16, 16, 16, 16);
        qr.setImageBitmap(qrCode());

        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF111114);
        root.setPadding(80, 48, 80, 48);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.rightMargin = 64;
        root.addView(text, textParams);
        root.addView(qr, new LinearLayout.LayoutParams(420, 420));
        setContentView(root);
    }

    private TextView label(int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private Bitmap qrCode() {
        String uri = store.setupUri(CATEGORY_TELEVISION);
        try {
            BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565);
            for (int y = 0; y < 512; y++) {
                for (int x = 0; x < 512; x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }

    private void update() {
        boolean paired = store.hasUser();
        boolean remote = io.github.henry753951.airplayremote.remote.input.RemoteAccessibilityService.Companion.getInstance() != null;
        title.setText(paired ? "Телевизор в «Доме»" : "Добавьте телевизор в «Дом»");
        hint.setText(paired
                ? "Пульт: Пункт управления на iPhone → значок пульта."
                : "На iPhone: приложение «Дом» → «+» → «Добавить аксессуар». Наведите камеру на код или введите:");
        pin.setText(store.getPin());
        pin.setVisibility(paired ? android.view.View.GONE : android.view.View.VISIBLE);
        qr.setVisibility(paired ? android.view.View.GONE : android.view.View.VISIBLE);
        state.setText("Сервер: " + HomeKitService.status
                
                + "\nПульт: " + (remote ? "готов" : "служба специальных возможностей выключена"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
