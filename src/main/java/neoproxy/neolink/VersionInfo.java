package neoproxy.neolink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static neoproxy.neolink.Debugger.debugOperation;


public class VersionInfo {
    public static final String VERSION = getAppVersion();
    public static final String AUTHOR = "Ceroxe";

    public static void outPutEula() {
        File eulaTXT = new File(System.getProperty("user.dir") + File.separator + "eula.txt");
        if (eulaTXT.exists()) {
            eulaTXT.delete();
        }
        if (!eulaTXT.exists()) {
            try {
                eulaTXT.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-3);
            }
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(eulaTXT, StandardCharsets.UTF_8));
                bufferedWriter.write("""
                        NeoLink 最终用户许可协议 (EULA)
                        
                        版本：1.0
                        生效日期：2025-11-1
                        
                        欢迎使用【软件名称】软件（以下简称“本软件”）。本最终用户许可协议（以下简称“本协议”）是您（个人或单一实体，以下简称“用户”）与【请填写您的开发者/公司名称】（以下简称“开发者”）之间关于使用本软件的法律协议。
                        
                        1. 协议的接受
                        在安装、复制、下载、访问或以其他方式使用本软件前，请您仔细阅读本协议的全部条款。一旦您实施了上述任一行为，即表示您已充分理解、同意并接受本协议的全部条款。如果您不同意本协议的任何条款，请立即停止使用本软件。
                        
                        2. 知识产权许可
                        开发者授予您一项非排他性的、不可转让的、可撤销的许可，允许您在本协议条款的约束下，为个人或内部商业目的使用本软件。本软件及其所有内容、功能、设计、知识产权（包括但不限于著作权、商标权、专利权等）均归开发者所有，受中华人民共和国法律和国际知识产权法的保护。未经开发者事先书面同意，您不得对本软件进行反向工程、反编译、反汇编、破解、出租、出借、分发或创建衍生作品。
                        
                        3. 用户行为规范与责任
                        您承诺，将严格遵守中华人民共和国现行有效的法律法规、社会公德及公共秩序，并独立对您使用本软件的一切行为承担全部法律责任。
                        
                        3.1. 禁止的用途
                        您不得利用本软件从事任何违法违规或侵犯他人合法权益的活动，包括但不限于：
                        a) 危害国家安全、泄露国家秘密、颠覆国家政权、破坏国家统一的；
                        b) 损害国家荣誉和利益的；
                        c) 煽动民族仇恨、民族歧视，破坏民族团结的；
                        d) 破坏国家宗教政策，宣扬邪教和封建迷信的；
                        e) 散布谣言，扰乱社会秩序，破坏社会稳定的；
                        f) 散布淫秽、色情、赌博、暴力、凶杀、恐怖或者教唆犯罪的；
                        g) 侮辱或者诽谤他人，侵害他人名誉权、隐私权、肖像权等合法权益的；
                        h) 侵入、干扰、破坏他人计算机信息系统或网络，或窃取他人数据的；
                        i) 传播病毒、木马或其他恶意代码的；
                        j) 从事任何形式的网络诈骗、传销、非法集资等活动的；
                        k) 侵犯他人知识产权、商业秘密的；
                        l) 未经授权访问或使用他人内网资源、设备或数据的；
                        m) 其他违反法律、行政法规、社会公德或公共秩序的行为。
                        
                        3.2. 用户内容责任
                        您通过本软件穿透内网后所访问、传输、存储或展示的任何数据、信息或内容（以下统称“用户内容”），其所有权及全部法律责任均由您本人承担。开发者作为中立的软件技术提供方，不对任何用户内容进行任何形式的审查、监控、编辑或背书。您保证您的用户内容及使用行为不违反本协议第3.1条的任何规定。
                        
                        4. 免责声明与责任限制
                        4.1. “按现状”提供
                        本软件按“现状”和“可用”状态提供。开发者不就本软件作任何明示或暗示的保证，包括但不限于对适销性、特定用途适用性、准确性、可靠性、不侵权性等任何保证。
                        
                        4.2. 责任免除
                        开发者明确声明，对于因您使用或无法使用本软件而导致的任何直接、间接、偶然、特殊、后果性或惩罚性的损害（包括但不限于利润损失、数据丢失、业务中断、商誉损害或其他经济损失），开发者均不承担任何责任，无论该等损害是基于保证、合同、侵权行为或任何其他法律理论，且无论开发者是否已被告知发生此种损害的可能性。
                        
                        4.3. 用户行为免责
                        您是您使用本软件行为的唯一责任主体。对于您或任何第三方利用本软件从事的任何行为所引发的任何争议、纠纷、行政处罚、诉讼、仲裁或任何形式的损失（包括但不限于律师费、诉讼费、赔偿金等），开发者概不负责。您同意赔偿开发者及其关联方因此遭受的全部损失和费用。
                        
                        4.4. “避风港”原则
                        开发者尊重他人的合法权益。如果我们依据相关法律法规或有权机关的要求，或在接到权利人的有效通知后，对您的相关行为或内容采取了删除、屏蔽、断开链接等必要措施，我们不因此承担任何责任，且保留向您追偿因我们采取该等措施而产生的合理费用的权利。
                        
                        5. 协议的终止
                        如果您违反了本协议的任何条款，开发者有权在不事先通知的情况下，立即终止您使用本软件的许可，并禁止您继续使用。协议终止后，您应立即销毁本软件的所有副本。
                        
                        6. 适用法律与争议解决
                        本协议的订立、效力、解释、履行及争议的解决均适用中华人民共和国法律。因本协议引起的或与本协议有关的任何争议，双方应友好协商解决；协商不成的，任何一方均有权向开发者所在地有管辖权的人民法院提起诉讼。
                        
                        7. 其他
                        本协议构成您与开发者之间关于使用本软件的完整协议，取代之前的所有口头或书面约定。如本协议的任何条款被认定为无效或不可执行，不影响其他条款的效力。开发者有权根据业务需要修订本协议，更新后的协议将在软件内或官方网站公布，您继续使用本软件即视为接受修订后的协议。
                        
                        English Version
                        NeoLink End-User License Agreement (EULA)
                        
                        Version: 1.0
                        Effective Date: 2025-11-1
                        
                        Welcome to [Software Name] software (the "Software"). This End-User License Agreement (the "Agreement") is a legal agreement between you (an individual or a single entity, the "User") and [Your Developer/Company Name Here] (the "Developer") regarding the use of the Software.
                        
                        1. Acceptance of the Agreement
                        Before installing, copying, downloading, accessing, or otherwise using the Software, please read carefully all the terms of this Agreement. By performing any of the aforementioned actions, you signify that you have fully understood, agreed to, and accept all terms of this Agreement. If you do not agree to any of the terms of this Agreement, please immediately cease using the Software.
                        
                        2. Intellectual Property License
                        The Developer grants you a non-exclusive, non-transferable, and revocable license to use the Software for personal or internal business purposes, subject to the terms and conditions of this Agreement. The Software and all its content, features, design, and intellectual property (including but not limited to copyrights, trademarks, patents, etc.) are the sole property of the Developer and are protected by the laws of the People's Republic of China and international intellectual property treaties. Without the prior written consent of the Developer, you may not reverse engineer, decompile, disassemble, crack, rent, lend, distribute, or create derivative works based on the Software.
                        
                        3. User Conduct and Responsibilities
                        You undertake to strictly comply with all current and effective laws and regulations of the People's Republic of China, public order, and social morality, and you shall be solely and fully responsible for all of your actions when using the Software.
                        
                        3.1. Prohibited Uses
                        You shall not use the Software for any illegal or infringing activities that violate the rights of others, including but not limited to:
                        a) Endangering national security, leaking state secrets, subverting state power, or undermining national unity;
                        b) Harming national honor and interests;
                        c) Inciting ethnic hatred or discrimination, and undermining national unity;
                        d) Undermining the state's religious policies, propagating cults or feudal superstition;
                        e) Spreading rumors, disturbing social order, or undermining social stability;
                        f) Spreading obscenity, pornography, gambling, violence, murder, terror, or abetting crime;
                        g) Insulting or defaming others, infringing upon their rights to reputation, privacy, portrait, etc.;
                        h) Intruding into, interfering with, or damaging another person's computer information system or network, or stealing another person's data;
                        i) Distributing viruses, Trojans, or other malicious code;
                        j) Engaging in any form of online fraud, pyramid schemes, illegal fundraising, etc.;
                        k) Infringing upon another person's intellectual property rights, trade secrets, etc.;
                        l) Accessing or using another person's intranet resources, devices, or data without authorization;
                        m) Any other acts that violate laws, administrative regulations, public morality, or public order.
                        
                        3.2. Responsibility for User Content
                        Any data, information, or content that you access, transmit, store, or display through the Software after penetrating the intranet (collectively, "User Content") is solely owned by and is the sole legal responsibility of you. The Developer, as a neutral provider of software technology, does not review, monitor, edit, or endorse any User Content in any form. You guarantee that your User Content and your use of the Software do not violate any of the provisions in Section 3.1 of this Agreement.
                        
                        4. Disclaimer of Warranty and Limitation of Liability
                        4.1. "AS IS" Provision
                        The Software is provided on an "AS IS" and "AS AVAILABLE" basis. The Developer makes no warranties of any kind, whether express or implied, regarding the Software, including but not limited to warranties of merchantability, fitness for a particular purpose, accuracy, reliability, or non-infringement.
                        
                        4.2. Limitation of Liability
                        THE DEVELOPER EXPRESSLY DISCLAIMS ANY AND ALL LIABILITY FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES (INCLUDING BUT NOT LIMITED TO LOSS OF PROFITS, LOSS OF DATA, BUSINESS INTERRUPTION, DAMAGE TO REPUTATION, OR ANY OTHER ECONOMIC LOSS) RESULTING FROM YOUR USE OR INABILITY TO USE THE SOFTWARE, WHETHER BASED ON WARRANTY, CONTRACT, TORT (INCLUDING NEGLIGENCE), OR ANY OTHER LEGAL THEORY, EVEN IF THE DEVELOPER HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
                        
                        4.3. Disclaimer for User Actions
                        You are the sole responsible party for your actions while using the Software. The Developer shall not be held liable for any disputes, disputes, administrative penalties, lawsuits, arbitrations, or any form of loss (including but not limited to attorney's fees, court costs, and compensation) arising from any actions undertaken by you or any third party using the Software. You agree to indemnify and hold the Developer and its affiliates harmless from all losses and expenses incurred in this regard.
                        
                        4.4. "Safe Harbor" Principle
                        The Developer respects the legitimate rights and interests of others. If we, in accordance with applicable laws and regulations or the requirements of competent authorities, or upon receiving a valid notice from a rights holder, take necessary measures such as deleting, blocking, or disconnecting links to your relevant actions or content, we shall not bear any liability for such actions and reserve the right to seek reimbursement from you for any reasonable expenses incurred by us in taking such measures.
                        
                        5. Termination of the Agreement
                        If you violate any provision of this Agreement, the Developer has the right to immediately terminate your license to use the Software without prior notice and prohibit you from further use. Upon termination, you must immediately destroy all copies of the Software.
                        
                        6. Governing Law and Dispute Resolution
                        The formation, validity, interpretation, performance, and dispute resolution of this Agreement shall be governed by the laws of the People's Republic of China. Any dispute arising from or in connection with this Agreement shall be settled through amicable negotiation between the parties; if negotiation fails, either party shall have the right to file a lawsuit with the people's court having jurisdiction in the location of the Developer.
                        
                        7. Miscellaneous
                        This Agreement constitutes the entire agreement between you and the Developer regarding the use of the Software and supersedes all prior oral or written agreements. If any provision of this Agreement is found to be invalid or unenforceable, the remaining provisions shall remain in full force and effect. The Developer reserves the right to amend this Agreement as necessary. The updated Agreement will be published within the Software or on the official website. Your continued use of the Software shall be deemed as acceptance of the revised Agreement.""");
                bufferedWriter.close();
            } catch (Exception e) {
                debugOperation(e);
            }
        }
    }

    private static String getAppVersion() {
        Properties props = new Properties();
        try (InputStream is = NeoLink.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Missing resource: " + "app.properties" + " (ensure it's in src/main/resources)");
            }
            props.load(is); // Java 9+ 默认 UTF-8，安全读取中文/特殊字符
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + "app.properties", e);
        }

        String version = props.getProperty("app.version");
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Property '" + "app.version" + "' is missing or empty in " + "app.properties");
        }

        return version.trim();
    }
}
