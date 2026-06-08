package com.star.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

public class ImageBase64 extends ImageView {

    public ImageBase64(Context context) {
        super(context);
    }

    public void setImageBase64(String image) {
        byte[] decodeImageBase64 = Base64.decode(image, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodeImageBase64, 0, decodeImageBase64.length);
        this.setImageBitmap(bitmap);
    }
}
