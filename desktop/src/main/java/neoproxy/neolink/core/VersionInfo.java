package neoproxy.neolink.core;

import neoproxy.neolink.NeoLink;
import neoproxy.neolink.config.ConfigOperator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static neoproxy.neolink.util.Debugger.debugOperation;

/**
 * 版本元数据与 EULA 写入器。
 *
 * <p>设计原因：
 * 打包后的应用应当信任资源过滤后的版本号，但开发与测试运行仍必须从同一份 Gradle
 * 真源解析版本，而不能依赖另一份硬编码常量。EULA 文本由单一模板渲染，确保输出文件
 * 不会与实际应用版本产生漂移。</p>
 */
public final class VersionInfo {
    private static final String VERSION_TOKEN = "__NEOLINK_VERSION__";
    private static final String BUILD_SCRIPT_NAME = "build.gradle.kts";
    private static final Pattern BUILD_SCRIPT_VERSION_PATTERN =
            Pattern.compile("(?m)^\\s*val\\s+neoLinkApiVersion\\s*=\\s*\"([^\"]+)\"");

    public static final String VERSION = getAppVersion();
    public static final String AUTHOR = "Ceroxe";

    private static final String EULA_TEMPLATE = """
            NeoLink 最终用户许可协议（EULA）
            
            版本：__NEOLINK_VERSION__
            生效日期：2025-11-1
            
            欢迎使用 NeoLink 软件（以下简称“本软件”）。本最终用户许可协议（以下简称“本协议”）是您（个人或单一实体，以下简称“用户”）与 Ceroxe（以下简称“开发者”）之间关于使用本软件的法律协议。
            
            1. 协议的接受
            在安装、复制、下载、访问或以其他方式使用本软件之前，请您仔细阅读本协议的全部条款。一旦您实施上述任一行为，即表示您已充分理解、同意并接受本协议的全部条款。如果您不同意本协议的任何条款，请立即停止使用本软件。

            2. 知识产权许可
            开发者授予您一项非独占、不可转让、可撤销的许可，允许您在本协议条款和条件约束下，将本软件用于个人或内部商业目的。本软件及其全部内容、功能、设计和知识产权（包括但不限于著作权、商标权、专利权等）均归开发者所有，并受中华人民共和国法律及国际知识产权条约保护。未经开发者事先书面同意，您不得对本软件进行反向工程、反编译、反汇编、破解、出租、出借、分发或基于本软件创建衍生作品。

            3. 用户行为与责任
            您承诺严格遵守中华人民共和国现行有效的法律法规、社会公德和公共秩序，并对您使用本软件时的全部行为独立承担全部法律责任。

            3.1. 禁止用途
            您不得将本软件用于任何违法或侵权活动，亦不得用于侵犯他人合法权益的行为，包括但不限于：
            a) 危害国家安全、泄露国家秘密、颠覆国家政权、破坏国家统一；
            b) 损害国家荣誉和利益；
            c) 煽动民族仇恨、民族歧视，破坏民族团结；
            d) 破坏国家宗教政策，宣扬邪教和封建迷信；
            e) 散布谣言，扰乱社会秩序，破坏社会稳定；
            f) 散布淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪；
            g) 侮辱或者诽谤他人，侵害他人名誉权、隐私权、肖像权等合法权益；
            h) 侵入、干扰、破坏他人计算机信息系统或者网络，或者窃取他人数据；
            i) 传播病毒、木马或者其他恶意代码；
            j) 从事任何形式的网络诈骗、传销、非法集资等活动；
            k) 侵犯他人知识产权、商业秘密等；
            l) 未经授权访问或使用他人内网资源、设备或数据；
            m) 其他违反法律、行政法规、社会公德或者公共秩序的行为。
            
            3.2. 用户内容责任
            您通过本软件穿透内网后访问、传输、存储或展示的任何数据、信息或内容（统称“用户内容”）均由您自行拥有并由您独立承担全部法律责任。开发者作为中立的软件技术提供方，不会以任何形式审查、监控、编辑或认可任何用户内容。您保证您的用户内容以及您对本软件的使用不违反本协议第 3.1 条的任何规定。
            
            4. 免责声明与责任限制
            4.1. “按现状”提供
            本软件按照“现状”和“可用”状态提供。开发者对本软件不作任何明示或默示担保，包括但不限于适销性、特定用途适用性、准确性、可靠性或不侵权等方面的担保。
            
            4.2. 责任限制
            对于因您使用或无法使用本软件而导致的任何直接、间接、附带、特殊、后果性或惩罚性损害（包括但不限于利润损失、数据丢失、业务中断、声誉受损或其他任何经济损失），无论其依据是否为担保、合同、侵权行为（包括过失）或其他任何法律理论，开发者均不承担任何责任，即使开发者已被告知发生此类损害的可能性。
            
            4.3. 用户行为免责
            您对自己使用本软件期间的行为承担全部责任。对于您或任何第三方使用本软件所引发的任何争议、行政处罚、诉讼、仲裁或任何形式的损失（包括但不限于律师费、诉讼费和赔偿金），开发者概不负责。您同意就因此而使开发者及其关联方遭受的全部损失和费用向其作出赔偿并使其免受损害。
            
            4.4. “避风港”原则
            开发者尊重他人的合法权益。如果我们根据适用的法律法规或有权机关的要求，或者在收到权利人的有效通知后，对您的相关行为或内容采取删除、屏蔽、断开链接等必要措施，则我们不因此承担任何责任，并保留就我们因采取该等措施而产生的合理费用向您追偿的权利。
            
            5. 协议的终止
            如果您违反本协议的任何条款，开发者有权在不事先通知的情况下立即终止您使用本软件的许可，并禁止您继续使用。本协议终止后，您应立即销毁本软件的所有副本。
            
            6. 适用法律与争议解决
            本协议的订立、效力、解释、履行及争议解决均适用中华人民共和国法律。因本协议引起或与本协议有关的任何争议，双方应先行友好协商解决；协商不成的，任何一方均有权向开发者所在地有管辖权的人民法院提起诉讼。
            
            7. 其他
            本协议构成您与开发者之间关于使用本软件的完整协议，并取代此前所有口头或书面约定。如果本协议任何条款被认定为无效或不可执行，不影响其他条款的效力。开发者保留根据业务需要修订本协议的权利。修订后的协议将发布于本软件内或官方网站上。您继续使用本软件即视为接受修订后的协议。
            """;

    private VersionInfo() {
    }

    public static void outPutEula() {
        File eulaFile = new File(resolveEulaDirectory(), "eula.txt");
        try {
            File parent = eulaFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }

            String renderedEula = renderedEula();
            String currentContent = eulaFile.exists()
                    ? Files.readString(eulaFile.toPath(), StandardCharsets.UTF_8)
                    : null;
            if (!renderedEula.equals(currentContent)) {
                Files.writeString(
                        eulaFile.toPath(),
                        renderedEula,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
        } catch (IOException e) {
            debugOperation(e);
        }
    }

    static String renderedEula() {
        return EULA_TEMPLATE.replace(VERSION_TOKEN, VERSION);
    }

    private static File resolveEulaDirectory() {
        if (ConfigOperator.WORKING_DIR != null && !ConfigOperator.WORKING_DIR.isBlank()) {
            return new File(ConfigOperator.WORKING_DIR);
        }
        return new File(System.getProperty("user.dir"));
    }

    private static String getAppVersion() {
        Properties properties = new Properties();
        try (InputStream inputStream = NeoLink.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (inputStream == null) {
                return versionFromBuildScriptOrFallback();
            }
            properties.load(inputStream);
        } catch (IOException e) {
            return versionFromBuildScriptOrFallback();
        }

        String version = properties.getProperty("app.version");
        if (version == null || version.isBlank() || version.contains("${")) {
            return versionFromBuildScriptOrFallback();
        }
        return version.trim();
    }

    private static String versionFromBuildScriptOrFallback() {
        String buildScriptVersion = findVersionFromBuildScript();
        return buildScriptVersion != null ? buildScriptVersion : "Dev-ver";
    }

    private static String findVersionFromBuildScript() {
        Path[] candidates = new Path[]{
                Path.of(System.getProperty("user.dir"), BUILD_SCRIPT_NAME),
                Path.of(NeoLink.CURRENT_DIR_PATH, BUILD_SCRIPT_NAME)
        };
        for (Path candidate : candidates) {
            String version = readVersionFromBuildScript(candidate);
            if (version != null) {
                return version;
            }
        }
        return null;
    }

    private static String readVersionFromBuildScript(Path buildScriptPath) {
        try {
            if (buildScriptPath == null || !Files.isRegularFile(buildScriptPath)) {
                return null;
            }
            String content = Files.readString(buildScriptPath, StandardCharsets.UTF_8);
            Matcher matcher = BUILD_SCRIPT_VERSION_PATTERN.matcher(content);
            if (!matcher.find()) {
                return null;
            }
            String version = matcher.group(1);
            return version == null || version.isBlank() ? null : version.trim();
        } catch (IOException e) {
            return null;
        }
    }
}
