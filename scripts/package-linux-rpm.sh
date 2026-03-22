#!/usr/bin/env bash
set -euo pipefail

APP_NAME="upbchat"
ICON_PATH="src/main/resources/chatupb-logo.png"
MAIN_CLASS="edu.upb.chatupb_v2.ChatUPB_V2"

mvn -q -DskipTests package
mvn -q -DskipTests org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory=target/lib

RAW_VERSION="$(mvn -q -DforceStdout -Dexpression=project.version help:evaluate)"
APP_VERSION="$(printf '%s' "${RAW_VERSION}" | sed 's/-SNAPSHOT//')"
JAR_NAME="ChatUPB_V2-${RAW_VERSION}.jar"

RPM_TOP="target/pkg/rpm"
RPM_BUILDROOT="${RPM_TOP}/BUILDROOT/${APP_NAME}-${APP_VERSION}-1.x86_64"
APP_ROOT="${RPM_BUILDROOT}/opt/${APP_NAME}"

rm -rf "${RPM_TOP}"
mkdir -p "${RPM_TOP}/"{BUILD,RPMS,SOURCES,SPECS,SRPMS} "${APP_ROOT}/lib"

install -Dm644 "target/${JAR_NAME}" "${APP_ROOT}/lib/${JAR_NAME}"
install -Dm644 target/lib/*.jar "${APP_ROOT}/lib/"
install -Dm644 "${ICON_PATH}" "${RPM_BUILDROOT}/usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png"
mkdir -p "${RPM_BUILDROOT}/usr/share/applications"

cat > "${APP_ROOT}/${APP_NAME}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "Java no encontrado. Instala Java 21+ (por ejemplo: java-21-openjdk) y vuelve a intentar."
  exit 1
fi

APP_DIR="/opt/upbchat"
MAIN_CLASS="edu.upb.chatupb_v2.ChatUPB_V2"
exec java -cp "${APP_DIR}/lib/*" "${MAIN_CLASS}" "$@"
EOF

chmod 755 "${APP_ROOT}/${APP_NAME}"
install -Dm755 "${APP_ROOT}/${APP_NAME}" "${RPM_BUILDROOT}/usr/bin/${APP_NAME}"

cat > "${RPM_BUILDROOT}/usr/share/applications/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=UPB Chat
Exec=${APP_NAME}
Icon=${APP_NAME}
Terminal=false
Categories=Network;Chat;
EOF

cat > "${RPM_TOP}/SPECS/${APP_NAME}.spec" <<EOF
Name:           ${APP_NAME}
Version:        ${APP_VERSION}
Release:        1%{?dist}
Summary:        UPB Chat JavaFX application
License:        Proprietary
URL:            https://example.invalid/
BuildArch:      x86_64
Requires:       java-21-openjdk

%description
JavaFX chat application for LAN/P2P messaging.

%install
mkdir -p %{buildroot}
cp -a ${RPM_BUILDROOT}/* %{buildroot}/

%files
/opt/${APP_NAME}
/usr/bin/${APP_NAME}
/usr/share/applications/${APP_NAME}.desktop
/usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png
EOF

rpmbuild -bb "${RPM_TOP}/SPECS/${APP_NAME}.spec" --define "_topdir ${RPM_TOP}"
mkdir -p dist
cp "${RPM_TOP}/RPMS/x86_64/${APP_NAME}-${APP_VERSION}-1"*.rpm dist/
