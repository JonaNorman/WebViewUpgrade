package com.norman.webviewup.demo.catalog;

/**
 * Maps {@link com.norman.webviewup.lib.util.ProcessUtils#getCurrentInstruction()} values
 * to {@code webview_packages.json} {@code arch} strings.
 */
public final class CatalogArch {

    private CatalogArch() {
    }

    public static boolean matchesDevice(String catalogArch, String deviceInstruction) {
        if (catalogArch == null || deviceInstruction == null) {
            return false;
        }
        switch (catalogArch) {
            case "arm32+64":
                return "arm".equals(deviceInstruction) || "arm64".equals(deviceInstruction);
            case "arm32":
                return "arm".equals(deviceInstruction);
            case "arm64":
                return "arm64".equals(deviceInstruction);
            case "x86":
                return "x86".equals(deviceInstruction) || "x86_64".equals(deviceInstruction);
            default:
                return false;
        }
    }
}
