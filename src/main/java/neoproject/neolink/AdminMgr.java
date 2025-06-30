package neoproject.neolink;

public class AdminMgr {
    public static int ADMIN_PORT = 945;

    private AdminMgr() {
        //TODO Somethings
    }

//    public static void LoginsAdministrator(String password){
//        try {
//            Socket socket=new Socket(REMOTE_DOMAIN_NAME,ADMIN_PORT);
//            BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            BufferedWriter bufferedWriter=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
//
//            sendStr(bufferedWriter,password);
//            String status=receiveStr(bufferedReader);
//            if (!status.contains("success")){
//                System.out.println("Verification could not be completed.");
//                System.exit(0);
//            }
//
//
//            Scanner scanner=new Scanner(System.in);
//            while (true){
//                System.out.print(">>>");
//                String command=scanner.nextLine();
//                sendStr(bufferedWriter,command);
//                System.out.println(receiveStr(bufferedReader));
//            }
//
//
//
//
//
//
//
//        }catch (Exception e){
//            e.printStackTrace();
//            System.exit(-1);
//        }
//    }
}
