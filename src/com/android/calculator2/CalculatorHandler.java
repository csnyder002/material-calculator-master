package com.android.calculator2;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * Created by Cody.Snyder on 6/21/2017.
 */

public class CalculatorHandler extends IntentService {
    private static HashMap<String,byte[]> myMap;
    private static Boolean bodyFlag;
    private static Boolean finalFlag;
    private static int totalFiles;
    private static String filename;

    public CalculatorHandler() {
        super("");
        myMap = new HashMap<>();
        finalFlag = false;
        bodyFlag = false;
        filename = "";
    }

    @Override
    protected void onHandleIntent(@Nullable Intent data) {
        try {
            String realPath;
            try {
                if (Build.VERSION.SDK_INT < 11) { // SDK < API11
                    realPath = CalculatorUtility.getRealPathFromURI_BelowAPI11(this, data.getData());
                } else if (Build.VERSION.SDK_INT < 19) { // SDK >= 11 && SDK < 19
                    realPath = CalculatorUtility.getRealPathFromURI_API11to18(this, data.getData());
                } else { // SDK > 19 (Android 4.4)
                    realPath = CalculatorUtility.getRealPathFromURI_API19(this, data.getData());
                }
            } catch (Exception e) {
                realPath = new File(data.getData().getPath()).getPath();
            }

            String[] splits = realPath.split("/");
            realPath = "";

            for (int i=0; i<splits.length-1; i++) {
                realPath += splits[i] + "/";
            }

            File directory = new File(realPath);
            File[] files = directory.listFiles();

            for (File file: files) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                    byte[] payload = decode_text(pixels);
                    processPayload(payload);
                } catch (Exception e) {
                    System.out.println("Unable to process file.");
                }
            }


            if (bodyFlag && finalFlag && !filename.equals("")) {
                System.out.println("Saving " + realPath + filename + "...");

                // calculate total size needed
                int size = 0;
                for (int i=1; i<=totalFiles; i++) {
                    size += myMap.get(i+"").length;
                }
                byte[] output = new byte[size];
                // put it all together
                int ctr = 0;
                byte[] temp;
                int sizes = 0;
                for (int i=1; i<=totalFiles; i++) {
                    temp = myMap.get(i+"");
                    sizes+=temp.length;
                    System.arraycopy(temp, 0, output, ctr, temp.length);
                    ctr += temp.length;
                }
                // save the file
                FileOutputStream fos = new FileOutputStream(realPath+filename);
                fos.write(output);
                fos.close();

                openFile(this.getApplicationContext(), new File(realPath+filename));


                updateMainThread("finished");

            } else {
                updateMainThread("error");
            }


        } catch (Exception e) {
            System.out.println("ON_ACTIVITY_RESULT " + e.toString());
            updateMainThread("error");
        }
        return;
    }

    private void updateMainThread(String str) {
        Intent intent = new Intent("com.android.calculator2");

        intent.putExtra("result", str);

        sendBroadcast(intent);
    };

    private void processPayload(byte[] payload) {
        try {
            byte flag = payload[0];
            if (flag == IntToByteArray(Integer.parseInt("00000000", 2))[3]) {
                // put payload with the  others
                byte[] treasure = new byte[payload.length - 2];
                System.arraycopy(payload, 2, treasure, 0, treasure.length);
                myMap.put(byteToUnsignedInt(payload[1]) + "", treasure);

                if (finalFlag) {
                    if (myMap.size() == totalFiles) {
                        bodyFlag = true;
                    }
                }
                updateMainThread("FILE #" + byteToUnsignedInt(payload[1]) + " found ("+treasure.length+" bytes)");

            } else if (flag == IntToByteArray(Integer.parseInt("11111111", 2))[3]) {

                int nameLen = byteToUnsignedInt(payload[2]) + 3;
                totalFiles = byteToUnsignedInt(payload[1]) - 1;
                filename = "";
                for (int i = 3; i < nameLen; i++) {
                    filename += (char) payload[i];
                }
                updateMainThread("FILENAME FILE FOUND: " + filename);
            } else if (flag == IntToByteArray(Integer.parseInt("00001111", 2))[3]) {
                finalFlag = true;
                totalFiles = byteToUnsignedInt(payload[1]);

                // strip filler bytes
                int last = byteToUnsignedInt(payload[payload.length - 1]);
                int i = payload.length - 2;
                while (last == byteToUnsignedInt(payload[i])) {
                    i--;
                }

                // put payload with the  others
                byte[] treasure = new byte[(payload.length - (1 + (payload.length - i)))];
                System.arraycopy(payload, 2, treasure, 0, treasure.length);
                myMap.put(byteToUnsignedInt(payload[1]) + "", treasure);

                // check if we have every file
                if (myMap.size() == totalFiles) {
                    bodyFlag = true;
                }

                updateMainThread("FILE #" + byteToUnsignedInt(payload[1]) + " found ("+treasure.length+" bytes)");
                //printByteArray(treasure);
            }
        } catch (Exception e) {
            System.out.println("PROCESS PAYLOAD: " + e.toString());
        }
    }

    private static int byteToUnsignedInt(byte b) {
        return 0x00 << 24 | b & 0xff;
    }

    private byte[] decode_text(int[] pixels) {
        // convert int[] to byte[] while stripping alpha values
        byte[] temp;
        byte[] pixelInfo = new byte[(pixels.length*3)];
        int ctr = 0;
        for (int pixel: pixels) {
            temp = IntToByteArray(pixel);
            pixelInfo[ctr] = temp[1];
            ctr++;
            pixelInfo[ctr] = temp[2];
            ctr++;
            pixelInfo[ctr] = temp[3];
            ctr++;
        }

        // collect lsbs
        int length = ((pixels.length*3) - (pixels.length*3)%8)/8;
        byte[] result = new byte[length];
        int offset = 0;
        for(int b = 0; b < result.length; b++) {
            for(int i=0; i<8; ++i, ++offset) {
                result[b] = (byte)((result[b] << 1) | (pixelInfo[offset] & 1));
            }
        }

        return result;
    }

    private static byte[] IntToByteArray( int data ) {
        byte[] result = new byte[4];
        result[0] = (byte) ((data & 0xFF000000) >> 24);
        result[1] = (byte) ((data & 0x00FF0000) >> 16);
        result[2] = (byte) ((data & 0x0000FF00) >> 8);
        result[3] = (byte) (data & 0x000000FF);
        return result;
    }

    private static void printByteArray(byte[] bytes) {
        int i = 0;
        System.out.println("vvvvvvvv");
        for (byte b : bytes) {
            i++;
            if (i==25) {
                System.out.println("--------");
            }
            if (i<24 || i >(bytes.length-24)) {
                System.out.println(Integer.toBinaryString(b & 255 | 256).substring(1));
            }
        }
        System.out.println("^^^^^^^^");
    }

    public static void openFile(Context context, File url) throws IOException {
        // Create URI
        File file=url;
        Uri uri = Uri.fromFile(file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        // Check what kind of file you are trying to open, by comparing the url with extensions.
        // When the if condition is matched, plugin sets the correct intent (mime) type,
        // so Android knew what application to use to open the file
        if (url.toString().contains(".doc") || url.toString().contains(".docx")) {
            // Word document
            intent.setDataAndType(uri, "application/msword");
        } else if(url.toString().contains(".pdf")) {
            // PDF file
            intent.setDataAndType(uri, "application/pdf");
        } else if(url.toString().contains(".ppt") || url.toString().contains(".pptx")) {
            // Powerpoint file
            intent.setDataAndType(uri, "application/vnd.ms-powerpoint");
        } else if(url.toString().contains(".xls") || url.toString().contains(".xlsx")) {
            // Excel file
            intent.setDataAndType(uri, "application/vnd.ms-excel");
        } else if(url.toString().contains(".zip") || url.toString().contains(".rar"))  {
            // ZIP Files
            intent.setDataAndType(uri, "application/zip");
        } else if(url.toString().contains(".rtf")) {
            // RTF file
            intent.setDataAndType(uri, "application/rtf");
        } else if(url.toString().contains(".wav") || url.toString().contains(".mp3")) {
            // WAV audio file
            intent.setDataAndType(uri, "audio/x-wav");
        } else if(url.toString().contains(".gif")) {
            // GIF file
            intent.setDataAndType(uri, "image/gif");
        } else if(url.toString().contains(".jpg") || url.toString().contains(".jpeg") || url.toString().contains(".png")) {
            // JPG file
            intent.setDataAndType(uri, "image/jpeg");
        } else if(url.toString().contains(".txt")) {
            // Text file
            intent.setDataAndType(uri, "text/plain");
        } else if(url.toString().contains(".apk")) {
            // Text file
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        } else if(url.toString().contains(".3gp") || url.toString().contains(".mpg") || url.toString().contains(".mpeg") || url.toString().contains(".mpe") || url.toString().contains(".mp4") || url.toString().contains(".avi")) {
            // Video files
            intent.setDataAndType(uri, "video/*");
        } else {
            //if you want you can also define the intent type for any other file

            //additionally use else clause below, to manage other unknown extensions
            //in this case, Android will show all applications installed on the device
            //so you can choose which application to use
            intent.setDataAndType(uri, "*/*");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

}
