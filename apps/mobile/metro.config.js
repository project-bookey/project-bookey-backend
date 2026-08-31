const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

/**
 * 앱을 자기 디렉터리 안에서 자족적으로 번들한다.
 * 저장소 루트에 워크스페이스 파일이 있어 Metro 가 상위를 감시하려 하는데,
 * 루트에는 설치된 모듈이 없으므로 감시 범위를 프로젝트로 고정한다.
 */
const projectRoot = __dirname;
const config = getDefaultConfig(projectRoot);

config.watchFolders = [projectRoot];
config.resolver.nodeModulesPaths = [path.resolve(projectRoot, 'node_modules')];
config.resolver.disableHierarchicalLookup = true;

module.exports = config;
