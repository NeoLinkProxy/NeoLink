package neoproject.neolink;

import plethora.management.bufferedFile.BufferedFile;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

import static neoproject.neolink.NeoLink.IS_DEBUG_MODE;

public class VersionInfo {
    public static final String VERSION = "3.2-RELEASE";
    public static final String AUTHOR = "Ceroxe";

    public static void outPutEula() {
        BufferedFile bufferedFile = new BufferedFile(System.getProperty("user.dir") + BufferedFile.separator + "eula.txt");
        if (!bufferedFile.exists()) {
            bufferedFile.createNewFile();
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(bufferedFile, StandardCharsets.UTF_8));
                bufferedWriter.write("""
                        1.本软件仅支持Minecraft TCP内网穿透,不支持其他用途的类型穿透
                        2.特此说明，严禁使用本软件用于从事非法活动，若用于非法用途，本作者将不承担任何责任（包括但不限于法律，社会等责任），同时积极配合相关机关调查。
                        3.此软件属于个人服务，如果有任何疑问，可以加QQ群 304509047 或加作者QQ 1591117599
                        4.作者本人对此软件的所有成分具有最终解释权
                        5.本eula以后可能有变动的可能，请以最新的为准
                        6.由于本内网穿透服务器采用的是竞价计费，业务若异常中断，请第一时间通知作者
                        
                        1. This software only supports Minecraft TCP intranet penetration and does not support other types of penetration for other purposes
                        2. It is hereby stated that it is strictly prohibited to use this software for illegal activities. If it is used for illegal purposes, the author will not bear any responsibility (including but not limited to legal, social and other responsibilities), and actively cooperate with relevant authorities to investigate.
                        3. This software belongs to personal service. If you have any questions, you can add QQ group 304509047 or author QQ 1591117599
                        4. The author has the right of final interpretation of all components of this software
                        5. This eula may change in the future, please take the latest one as the standard
                        6. Since the intranet penetrating server adopts auction billing, if the business is abnormally interrupted, please notify the author as soon as possible""");
                bufferedWriter.close();
            } catch (Exception e) {
                if (IS_DEBUG_MODE) {
                    e.printStackTrace();
                }
                e.printStackTrace();
            }
        }
    }
}
