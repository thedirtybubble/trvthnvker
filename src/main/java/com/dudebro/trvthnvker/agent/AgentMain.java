package com.dudebro.trvthnvker.agent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarFile;

public final class AgentMain {

    private AgentMain() {
    }

	public static void premain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst);
    }

    private static void initialize(String agentArgs, Instrumentation inst) {
        boolean stripRealms = parseStripRealmsOption(agentArgs);
        boolean stripChatKeys = parseStripChatKeysOption(agentArgs);
        UrlHook.STRIP_REALMS = stripRealms;
        UrlHook.STRIP_CHAT_KEYS = stripChatKeys;

        try {
            URL location = AgentMain.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                File agentJar = new File(location.toURI());
                if (agentJar.isFile()) {
                    inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
                }
            }
        } catch (URISyntaxException | IOException e) {
            System.err.println("[trvthnvker] unable to append agent to bootstrap classloader search: " + e.getMessage());
        }

        inst.addTransformer(new TelemetryTransformer(stripRealms, stripChatKeys), true);
        System.out.println("[trvthnvker] telemetry nvked");
        System.out.println("[trvthnvker] woke propaganda nvked");
        if (stripRealms) {
            System.out.println("[trvthnvker] realms nvked");
        }
        if (stripChatKeys) {
            System.out.println("[trvthnvker] chat signing nvked");
        }
    }

    private static boolean parseStripRealmsOption(String agentArgs) {
        return isOptionEnabled(agentArgs, "strip-realms", "striprealms", "disable-realms", "disablerealms", "no-realms", "norealms");
    }

    private static boolean parseStripChatKeysOption(String agentArgs) {
        return isOptionEnabled(agentArgs, "strip-chat-keys", "stripchatkeys", "disable-chat-keys", "disablechatkeys", "no-chat-keys", "nochatkeys");
    }

    private static boolean isOptionEnabled(String agentArgs, String... keys) {
        if (agentArgs != null) {
            String lower = agentArgs.toLowerCase(java.util.Locale.ROOT);
            for (String key : keys) {
                if (lower.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }
        for (String key : keys) {
            String val = System.getProperty(key);
            if (val != null && (val.isEmpty() || "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val))) {
                return true;
            }
            String prefixed = System.getProperty("trvthnvker." + key);
            if (prefixed != null && (prefixed.isEmpty() || "true".equalsIgnoreCase(prefixed) || "1".equals(prefixed) || "yes".equalsIgnoreCase(prefixed))) {
                return true;
            }
        }
        return false;
    }
}
