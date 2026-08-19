Pod::Spec.new do |spec|
    spec.name                     = 'proximityverification'
    spec.version                  = '0.5.0'
    spec.homepage                 = "https://github.com/italia/eudi-wallet-it-iso-verifier"
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'proximityverification Shared Module'
    spec.vendored_frameworks      = 'proximityverification.framework'
    spec.ios.deployment_target    = '16.0'
    spec.xcconfig = {
      'SWIFT_INCLUDE_PATHS' => '$(PROJECT_DIR)/Pods/**',
      'FRAMEWORK_SEARCH_PATHS' => '$(PROJECT_DIR)/Pods/shared/**'
    }
    spec.user_target_xcconfig = {
      'SWIFT_INCLUDE_PATHS' => '$(PROJECT_DIR)/Pods/**',
      'FRAMEWORK_SEARCH_PATHS' => '$(PROJECT_DIR)/Pods/shared/**'
    }
    spec.pod_target_xcconfig = {
      'SWIFT_INCLUDE_PATHS' => '$(PROJECT_DIR)/Pods/**',
      'FRAMEWORK_SEARCH_PATHS' => '$(PROJECT_DIR)/Pods/shared/**'
    }         
end