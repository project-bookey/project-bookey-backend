package app.bookey.domain.appconfig;

import java.util.regex.Pattern;

/**
 * 앱 버전 문자열(major.minor.patch) 비교. 빌드 번호·접미사(-beta 등)는 보지 않는다.
 * 앱이 보낸 값이 형식에 안 맞으면 비교할 수 없는 것으로 보고 업데이트를 강제하지 않는다 — 잘못된 값 하나로 모두를 막지 않게.
 */
public final class AppVersion {

    private static final Pattern FORMAT = Pattern.compile("\\d{1,4}(\\.\\d{1,4}){0,2}");

    private AppVersion() {
    }

    public static boolean isValid(String version) {
        return version != null && FORMAT.matcher(version.trim()).matches();
    }

    /** a < b 면 음수, 같으면 0, 크면 양수. 빠진 자리는 0 으로 본다(1.2 == 1.2.0). */
    public static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < 3; i++) {
            if (x[i] != y[i]) {
                return Integer.compare(x[i], y[i]);
            }
        }
        return 0;
    }

    /** current 가 floor 보다 낮은지. current 를 알 수 없으면 false. */
    public static boolean isBelow(String current, String floor) {
        if (!isValid(current) || !isValid(floor)) {
            return false;
        }
        return compare(current, floor) < 0;
    }

    private static int[] parts(String version) {
        int[] out = new int[3];
        String[] split = version.trim().split("\\.");
        for (int i = 0; i < Math.min(3, split.length); i++) {
            out[i] = Integer.parseInt(split[i]);
        }
        return out;
    }
}
