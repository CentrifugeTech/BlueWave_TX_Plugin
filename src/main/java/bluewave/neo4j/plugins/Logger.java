package bluewave.neo4j.plugins;
import java.util.List;
import java.util.LinkedList;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


//******************************************************************************
//**  Logger Class
//******************************************************************************
/**
 *   Used to log server activity to a text file. A new text file is created
 *   for each day.
 *
 ******************************************************************************/

public class Logger implements Runnable {

    private static List pool = new LinkedList();
    private File logDir;
    private FileChannel outChannel;
    private FileOutputStream outputFile;
    private int date;
    private java.util.TimeZone tz;
    private Long maxFileSize;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Logger(File logDir) {
        this(logDir, null, "UTC");
    }


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Logger(File logDir, Long maxFileSize, String timezone) {
        if (!logDir.exists()) logDir.mkdirs();
        this.logDir = logDir;
        this.date = -1;
        this.maxFileSize = maxFileSize;
        this.tz = javaxt.utils.Date.getTimeZone(timezone);
    }


  //**************************************************************************
  //** log
  //**************************************************************************
  /** Used to add a string to the log file. The string will be terminated with
   *  a line break.
   */
    public void log(String str){
        if (str!=null){
            if (!str.endsWith("\r\n")) str+= "\r\n";
            synchronized (pool) {
               pool.add(pool.size(), str);
               pool.notifyAll();
            }
        }
    }


  //**************************************************************************
  //** run
  //**************************************************************************
  /** Used to remove an entry from the queue and write it to a file.
   */
    public void run() {
        while (true) {

            String request = null;
            synchronized (pool) {
                while (pool.isEmpty()) {
                  try {
                    pool.wait();
                  }
                  catch (InterruptedException e) {
                      break;
                  }
                }
                request = (String) pool.remove(0);
            }



            try{
                byte[] b = request.getBytes();
                if (b.length>8*1024){
                    if (request.length()>250) request = request.substring(0, 250);
                    request += "...\r\n";
                    b = request.getBytes();
                }
                ByteBuffer output = ByteBuffer.allocateDirect(b.length);
                output.put(b);
                output.flip();
                getFileChannel().write(output);
            }
            catch(Exception e){
                e.printStackTrace();
            }

        }
    }


    private FileChannel getFileChannel() throws Exception {

        int date = Integer.parseInt(new SimpleDateFormat("yyyyMMdd").format(new java.util.Date()));
        if (date>this.date){
            this.date = date;

            if (outputFile!=null) outputFile.close();
            if (outChannel!=null) outChannel.close();

            File file = new File(logDir, this.date + ".log");
            outputFile = new FileOutputStream(file, true);
            outChannel = outputFile.getChannel();
        }
        else{
            File file = new File(logDir, this.date + ".log");
            if (maxFileSize!=null){
                if (file.length()>maxFileSize){
                    if (outputFile!=null) outputFile.close();
                    if (outChannel!=null) outChannel.close();
                    throw new Exception("Log file too big: " + file.length() + " vs " + maxFileSize);
                }
            }
        }
        return outChannel;
    }


    public javaxt.utils.Date getDate(){
        javaxt.utils.Date d = new javaxt.utils.Date();
        d.setTimeZone(tz);
        return d;
    }
}