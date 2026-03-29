package com.norman.webviewup.demo.catalog;

/**
 * Which concrete {@link com.norman.webviewup.lib.source.UpgradeSource} to build from
 * {@link UpgradeSourceParams}. Demo-only; not part of the core AAR.
 */
public enum UpgradeSourceKind {
    NETWORK,
    ASSET,
    INSTALLED_PACKAGE
}
