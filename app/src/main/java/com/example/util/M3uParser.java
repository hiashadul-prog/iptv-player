package com.example.util;

import com.example.data.Channel;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {
    public static List<Channel> parse(InputStream inputStream) {
        List<Channel> channels = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream));
            String line;
            String currentName = "";
            String currentLogo = "";
            String currentGroup = "Default";
            String currentTvgId = "";

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("#EXTINF:")) {
                    int commaIndex = trimmed.lastIndexOf(',');
                    if (commaIndex != -1 && commaIndex < trimmed.length() - 1) {
                        currentName = trimmed.substring(commaIndex + 1).trim();
                    } else {
                        currentName = "Unknown Channel";
                    }

                    currentLogo = extractAttribute(trimmed, "tvg-logo");
                    if (currentLogo.isEmpty()) {
                        currentLogo = extractAttribute(trimmed, "logo");
                    }

                    currentGroup = extractAttribute(trimmed, "group-title");
                    if (currentGroup.isEmpty()) {
                        currentGroup = "Default";
                    }

                    currentTvgId = extractAttribute(trimmed, "tvg-id");
                    if (currentTvgId.isEmpty()) {
                        currentTvgId = extractAttribute(trimmed, "id");
                    }

                } else if (!trimmed.startsWith("#")) {
                    if (!trimmed.isEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://"))) {
                        String name = currentName.isEmpty() ? "Channel " + (channels.size() + 1) : currentName;
                        channels.add(new Channel(
                                0,
                                name,
                                trimmed,
                                currentLogo,
                                currentGroup,
                                false,
                                currentTvgId,
                                currentGroup
                        ));
                        currentName = "";
                        currentLogo = "";
                        currentGroup = "Default";
                        currentTvgId = "";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return channels;
    }

    private static String extractAttribute(String line, String attributeName) {
        Pattern quotedPattern = Pattern.compile(
                attributeName + "\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
        );
        Matcher quotedMatcher = quotedPattern.matcher(line);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }

        Pattern unquotedPattern = Pattern.compile(
                attributeName + "\\s*=\\s*([^\\s,]+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher unquotedMatcher = unquotedPattern.matcher(line);
        if (unquotedMatcher.find()) {
            return unquotedMatcher.group(1);
        }

        return "";
    }
}
