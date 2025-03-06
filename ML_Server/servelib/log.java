package servelib;
import java.text.SimpleDateFormat;
import java.util.Date;

public class log {
    private static final String Reset = "\033[0m";// 清除
    private static final String Red = "\033[31m";
    private static final String Yellow = "\033[33m";
    private static final String Blue = "\033[34m";
    // 下发代码暂时没有被使用
    // private static final String Purple = "\033[35m";// 紫色
    // private static final String Cyan = "\033[36m";// 浅蓝
    // private static final String White = "\033[37m";
    // private static final String Green = "\033[32m";
    // private static final String Black = "\033[30m";

    private static boolean LOG_OnlyAtlevel = false; // 只
    private static LOGX SHOW_LOGX = LOGX.INFO;

    // 定义日志级别
    public static enum LOGX {
        ALL(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), FATAL(5), MUST(6);

        private final int value;

        LOGX(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // 定义日志头类型
    public static enum LOGH {
        ROOT(1), SYSTEM(2), TIME(3);

        private final int value;

        LOGH(int value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        public int getValue() {
            return value;
        }
    }

    // 默认参数：打印系统日志级别为INFO
    public void print(LOGX level, String logData) {
        print(LOGH.SYSTEM, level, logData); // 默认头部为 SYSTEM
    }

    // 默认参数：打印 INFO 日志级别
    public void print(LOGH head, String logData) {
        print(head, LOGX.INFO, logData); // 默认级别为 INFO
    }

    // 不携带任何参数
    public void print(String logData) {
        print(LOGH.SYSTEM, LOGX.INFO, logData); // 默认级别为 INFO
    }

    // 完整的日志打印方法，传入 head, level 和 logData
    public void print(LOGH head, LOGX level, String logData) {
        String headbuild = "";
        String levelBuild = "";

        // 设置日志头部
        switch (head) {
            case ROOT:
                headbuild = String.format("[ %s#ROOT#%S ]", Red, Reset);
                break;
            case SYSTEM:
                headbuild = String.format("[ %sSYSTEM%s ]", Blue, Reset);
                break;
            case TIME:
                headbuild = String.format("[%s]", new SimpleDateFormat("HH:mm:ss").format(new Date()));
                break;
        }

        // 设置日志级别
        switch (level) {
            case ALL:
                levelBuild = "[A]";
                break;
            case DEBUG:
                levelBuild = "[" + Yellow + "D" + Reset + "]";
                break;
            case INFO:
                levelBuild = "[" + Blue + "I" + Reset + "]";
                break;
            case ERROR:
                levelBuild = "[" + Red + "E" + Reset + "]";
                break;
            case FATAL:
                levelBuild = "[F]";
                break;
            case MUST:
                levelBuild = "[M]";
                break;
            default:
                break;
        }

        // 输出日志
        if (LOG_OnlyAtlevel) {
            if (SHOW_LOGX.getValue() == level.getValue()) {
                System.out.println(headbuild + levelBuild + " | " + logData);
            }
        } else {
            if (SHOW_LOGX.getValue() <= level.getValue()) {
                System.out.println(headbuild + levelBuild + " | " + logData);
            }
        }
    }
}
