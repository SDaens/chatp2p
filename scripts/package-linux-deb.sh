#!/usr/bin/env bash
set -euo pipefail

APP_NAME="upbchat"
ICON_PATH="src/main/resources/chatupb-logo.png"
MAIN_CLASS="edu.upb.chatupb_v2.ChatUPB_V2"

mvn -q -DskipTests package
mvn -q -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

RAW_VERSION="$(mvn -q -DforceStdout -Dexpression=project.version help:evaluate)"
APP_VERSION="$(printf '%s' "${RAW_VERSION}" | sed 's/-SNAPSHOT//')"
JAR_NAME="ChatUPB_V2-${RAW_VERSION}.jar"

PKG_ROOT="target/pkg/deb/${APP_NAME}"
APP_ROOT="${PKG_ROOT}/opt/${APP_NAME}"
DEBIAN_DIR="${PKG_ROOT}/DEBIAN"

rm -rf "${PKG_ROOT}"
mkdir -p "${APP_ROOT}/lib" "${DEBIAN_DIR}"

install -Dm644 "target/${JAR_NAME}" "${APP_ROOT}/lib/${JAR_NAME}"
install -Dm644 target/lib/*.jar "${APP_ROOT}/lib/"
install -Dm644 "${ICON_PATH}" "${PKG_ROOT}/usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png"

cat > "${APP_ROOT}/${APP_NAME}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "Java no encontrado. Instala Java 21+ (por ejemplo: openjdk-21-jre) y vuelve a intentar."
  exit 1
fi

APP_DIR="/opt/upbchat"
MAIN_CLASS="edu.upb.chatupb_v2.ChatUPB_V2"
exec java -cp "${APP_DIR}/lib/*" "${MAIN_CLASS}" "$@"
EOF

chmod 755 "${APP_ROOT}/${APP_NAME}"
install -Dm755 "${APP_ROOT}/${APP_NAME}" "${PKG_ROOT}/usr/bin/${APP_NAME}"

cat > "${PKG_ROOT}/usr/share/applications/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=UPB Chat
Exec=${APP_NAME}
Icon=${APP_NAME}
Terminal=false
Categories=Network;Chat;
EOF

cat > "${DEBIAN_DIR}/control" <<EOF
Package: ${APP_NAME}
Version: ${APP_VERSION}
Section: net
Priority: optional
Architecture: all
Depends: default-jre | openjdk-21-jre
Maintainer: UPB Chat Team
Description: UPB Chat JavaFX application
 JavaFX chat application for LAN/P2P messaging.
EOF

mkdir -p dist
fakeroot dpkg-deb --build "${PKG_ROOT}" "dist/${APP_NAME}_${APP_VERSION}_all.deb"
