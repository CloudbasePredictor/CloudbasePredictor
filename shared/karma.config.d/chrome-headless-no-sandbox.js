// GitHub-hosted ubuntu-latest runners (Ubuntu 24.04) disable unprivileged user namespaces via
// AppArmor, so Chrome's sandbox cannot start and ChromeHeadless aborts with "No usable sandbox".
// Run the Kotlin/Wasm browser tests through a launcher that passes --no-sandbox. Harmless locally.
config.set({
    browsers: ["ChromeHeadlessNoSandbox"],
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: "ChromeHeadless",
            flags: ["--no-sandbox", "--disable-dev-shm-usage"],
        },
    },
});
