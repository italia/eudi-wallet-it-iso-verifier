import proximityverification
// import FirebaseCrashlytics

class MyLoggerDelegate: LoggerDelegate {
    func log(level: LogLevel, tag: String, message: String) {
        print("[\(level.name)][\(tag)] \(message)")
        // Se vuoi anche Crashlytics (decommenta se hai Firebase Crashlytics):
        // Crashlytics.crashlytics().log("[\(level.name)][\(tag)] \(message)")
        // if level == .ERROR {
        //     let err = NSError(domain: tag, code: 0, userInfo: [NSLocalizedDescriptionKey: message])
        //     Crashlytics.crashlytics().record(error: err)
        // }
    }
}
