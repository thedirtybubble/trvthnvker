package com.dudebro.trvthnvker.agent;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UrlHook {

    public static volatile boolean STRIP_REALMS = false;
    public static volatile boolean STRIP_CHAT_KEYS = false;

    private static final String[] BLOCKED_HOST_SUFFIXES = new String[]{
        "telemetry.minecraft.net",
        "snoop.minecraft.net",
        "vortex.data.microsoft.com",
        "pipe.aria.microsoft.com",
        "browser.pipe.aria.microsoft.com",
        "events.data.microsoft.com",
        "watson.telemetry.microsoft.com",
        "watson.ppe.telemetry.microsoft.com",
        "vortex-win.data.microsoft.com",
        "mobile.events.data.microsoft.com"
    };

    private static final String[] REALMS_HOST_SUFFIXES = new String[]{
        "realms.minecraft.net"
    };

    private UrlHook() {
    }

    public static URLConnection openConnection(URL url) throws IOException {
        if (isBlockedUrl(url)) {
            return new NullHttpURLConnection(url, isRealmsUrl(url));
        }
        return url.openConnection();
    }

    public static URLConnection openConnection(URL url, java.net.Proxy proxy) throws IOException {
        if (isBlockedUrl(url)) {
            return new NullHttpURLConnection(url, isRealmsUrl(url));
        }
        return (proxy == null) ? url.openConnection() : url.openConnection(proxy);
    }

    public static boolean isBlockedUrl(URL url) {
        if (url == null) {
            return false;
        }
        String host = url.getHost();
        return isTelemetryHost(host) || (STRIP_REALMS && isRealmsHost(host));
    }

    public static boolean isRealmsUrl(URL url) {
        return url != null && isRealmsHost(url.getHost());
    }

    public static boolean isTelemetryUrl(URL url) {
        return url != null && isTelemetryHost(url.getHost());
    }

    public static boolean shouldIntercept(String host) {
        return isTelemetryHost(host) || (STRIP_REALMS && isRealmsHost(host));
    }

    public static boolean isTelemetryHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (int i = 0; i < BLOCKED_HOST_SUFFIXES.length; i++) {
            String suffix = BLOCKED_HOST_SUFFIXES[i];
            if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRealmsHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (int i = 0; i < REALMS_HOST_SUFFIXES.length; i++) {
            String suffix = REALMS_HOST_SUFFIXES[i];
            if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    public static Object filterSplash(Object splash) {
        if (splash == null) {
            return null;
        }
        if (splash instanceof String) {
            return filterWokeText((String) splash);
        }
        try {
            Method getStringMethod = null;
            Method[] methods = splash.getClass().getMethods();
            for (Method m : methods) {
                if (m.getParameterCount() == 0 && m.getReturnType() == String.class && "getString".equals(m.getName())) {
                    getStringMethod = m;
                    break;
                }
            }
            if (getStringMethod == null) {
                for (Method m : methods) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class && ("asString".equals(m.getName()) || m.getName().startsWith("getString"))) {
                        getStringMethod = m;
                        break;
                    }
                }
            }

            if (getStringMethod != null) {
                getStringMethod.setAccessible(true);
                String original = (String) getStringMethod.invoke(splash);
                if (original != null) {
                    String filtered = filterWokeText(original);
                    if (!original.equals(filtered)) {
                        Object newComp = createComponent(splash, filtered);
                        if (newComp != null) {
                            return newComp;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return splash;
    }

    private static Object createComponent(Object sample, String text) {
        Class<?> clazz = sample.getClass();
        List<Class<?>> candidates = new ArrayList<>();
        candidates.add(clazz);
        for (Class<?> iface : clazz.getInterfaces()) {
            if (!candidates.contains(iface)) {
                candidates.add(iface);
            }
        }
        for (Class<?> c = clazz.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (!candidates.contains(c)) {
                candidates.add(c);
            }
            for (Class<?> iface : c.getInterfaces()) {
                if (!candidates.contains(iface)) {
                    candidates.add(iface);
                }
            }
        }

        String[] preferredNames = new String[]{"literal", "method_43470", "of", "m_237113_", "translatable", "method_43471"};
        for (String name : preferredNames) {
            for (Class<?> c : candidates) {
                try {
                    Method m = c.getMethod(name, String.class);
                    if (Modifier.isStatic(m.getModifiers())) {
                        m.setAccessible(true);
                        return m.invoke(null, text);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        for (Class<?> c : candidates) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == String.class
                        && !m.getReturnType().equals(String.class)
                        && !m.getReturnType().equals(void.class)) {
                    try {
                        m.setAccessible(true);
                        return m.invoke(null, text);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    public static String filterWokeText(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean modified = false;

        if (lower.contains("be anti-racist")) {
            text = text.replaceAll("(?i)be anti-racist!?", "Drink whole milk!");
            modified = true;
        } else if (lower.contains("anti-racist")) {
            text = text.replaceAll("(?i)anti-racist", "milk-loving");
            modified = true;
        }

        if (lower.contains("anti-racism")) {
            text = text.replaceAll("(?i)anti-racism", "dairy farming");
            modified = true;
        }

        if (lower.contains("allyship")) {
            text = text.replaceAll("(?i)allyship", "pasteurization");
            modified = true;
        }

        if (lower.contains("bipoc voices")) {
            text = text.replaceAll("(?i)bipoc voices", "cow moos");
            modified = true;
        } else if (lower.contains("bipoc")) {
            text = text.replaceAll("(?i)bipoc", "dairy cow");
            modified = true;
        }

        if (lower.contains("injustice")) {
            text = text.replaceAll("(?i)injustice", "spilt milk");
            modified = true;
        }

        if (lower.contains("equality")) {
            text = text.replaceAll("(?i)equality", "milk and cookies");
            modified = true;
        }

        if (lower.contains("lives matter")) {
            text = text.replaceAll("(?i)\\b\\S+(?=\\s+lives matter)", "Milk");
            modified = true;
        }

        if (lower.contains("rights")) {
            text = text.replaceAll("(?i)\\b\\S+(?=\\s+rights)", "Milk");
            modified = true;
        }

        if (lower.contains("pride")) {
            text = text.replaceAll("(?i)pride[ _\\.]?month", "Milk Month").replaceAll("(?i)pride", "Milk");
            modified = true;
        }

        if (modified) {
            lower = text.toLowerCase(Locale.ROOT);
        }

        if (lower.contains("anti-racist") || lower.contains("anti-racism")
                || lower.contains("allyship") || lower.contains("injustice")
                || lower.contains("equality") || lower.contains("lgbtq")
                || lower.contains("aapi") || lower.contains("gender")
                || lower.contains("transgender") || lower.contains("racism")
                || lower.contains("bipoc")) {
            return "Got milk?";
        }
        return text;
    }
}