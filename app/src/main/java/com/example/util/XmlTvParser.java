package com.example.util;

import android.util.Xml;
import com.example.data.EpgProgram;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class XmlTvParser {
    public static List<EpgProgram> parse(InputStream inputStream) {
        List<EpgProgram> programs = new ArrayList<>();
        try {
            // Auto-detect GZIP Compression using Magic Bytes (0x1f, 0x8b)
            PushbackInputStream pushbackStream = new PushbackInputStream(inputStream, 2);
            byte[] signature = new byte[2];
            int read = pushbackStream.read(signature);
            if (read > 0) {
                pushbackStream.unread(signature, 0, read);
            }

            InputStream finalStream;
            if (read >= 2 && signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b) {
                finalStream = new GZIPInputStream(pushbackStream);
            } else {
                finalStream = pushbackStream;
            }

            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(finalStream, "UTF-8");

            int eventType = parser.getEventType();
            String currentChannelId = null;
            long currentStartTime = 0L;
            long currentEndTime = 0L;
            String currentTitle = "";
            String currentDesc = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("programme".equals(tagName)) {
                            currentChannelId = parser.getAttributeValue(null, "channel");
                            String startStr = parser.getAttributeValue(null, "start");
                            String stopStr = parser.getAttributeValue(null, "stop");
                            currentStartTime = parseXmlTvDate(startStr != null ? startStr : "");
                            currentEndTime = parseXmlTvDate(stopStr != null ? stopStr : "");
                            currentTitle = "";
                            currentDesc = "";
                        } else if ("title".equals(tagName) && currentChannelId != null) {
                            currentTitle = parser.nextText();
                            if (currentTitle == null) currentTitle = "";
                        } else if ("desc".equals(tagName) && currentChannelId != null) {
                            currentDesc = parser.nextText();
                            if (currentDesc == null) currentDesc = "";
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("programme".equals(tagName) && currentChannelId != null) {
                            programs.add(new EpgProgram(
                                    0,
                                    currentChannelId,
                                    currentTitle.trim(),
                                    currentDesc.trim(),
                                    currentStartTime,
                                    currentEndTime
                            ));
                            currentChannelId = null;
                        }
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return programs;
    }

    private static long parseXmlTvDate(String dateStr) {
        String cleanStr = dateStr.trim();
        String[] formats = {
                "yyyyMMddHHmmss Z",
                "yyyyMMddHHmmss",
                "yyyy-MM-dd HH:mm:ss Z",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
                java.util.Date d = sdf.parse(cleanStr);
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception e) {
                // Keep trying
            }
        }

        // Fallback: search for first 14 digits to form yyyyMMddHHmmss
        StringBuilder digitOnly = new StringBuilder();
        for (int i = 0; i < cleanStr.length(); i++) {
            char ch = cleanStr.charAt(i);
            if (Character.isDigit(ch)) {
                digitOnly.append(ch);
            }
        }
        if (digitOnly.length() >= 14) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                java.util.Date d = sdf.parse(digitOnly.substring(0, 14));
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception e) {
                // Ignored
            }
        }
        return 0L;
    }
}
