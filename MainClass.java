import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Hashtable;
class Disk 
{
    static final int NUM_SECTORS = 2048;
    static final int DISK_DELAY = 80; 
    public int free_sector_at = 0;
    public StringBuffer sectors[] = new StringBuffer[NUM_SECTORS];
    Disk()
    {
    }
    void write(int sector, StringBuffer data)
    {
        try 
        {
            Thread.sleep(DISK_DELAY);
            free_sector_at += 1;
            sectors[sector] = new StringBuffer(data);
        }
        catch(Exception e) 
        { 
            System.out.println(e); 
        }
    }
    StringBuffer read(int sector, StringBuffer data)
    {
        try 
        {
            Thread.sleep(DISK_DELAY);
            data = sectors[sector];
            return sectors[sector];  
        }
        catch(Exception e) 
        {
            System.out.println(e); 
            return null;
        }
    }
}

class Printer 
{
    static final int PRINT_DELAY = 275;
    int id;
    Printer(int id)
    {
        this.id = id;
    }
    void print(StringBuffer data)
    {
        try 
        {
            String printer_name = "PRINTER" + Integer.toString(this.id);
            Thread.sleep(PRINT_DELAY);
            try (FileWriter printed_file = new FileWriter(printer_name, true)) 
            {
                printed_file.write(data.toString() + "\n");
                printed_file.flush();
            }
        }
        catch(Exception e) 
        { 
            System.out.println(e); 
        }
    }
}

class PrintJobThread extends Thread
{
    StringBuffer line = new StringBuffer(); 
    int id;
    String file_name;
    DiskManager disk_manager;
    PrinterManager printer_manager;
    PrintJobThread(DiskManager disk_manager, PrinterManager printer_manager, String file, int current_printer)
    {
        this.disk_manager = disk_manager;
        this.printer_manager = printer_manager;
        this.id = current_printer;
        this.file_name = file;
    }

    public void run()
    {
        try 
        {
            StringBuffer new_file = new StringBuffer(this.file_name);
            FileInfo file_info = disk_manager.directory.lookup(new_file);
            for (int i = 0; i < file_info.fileLength; i++) {
                Disk current_disk = disk_manager.all_disks[file_info.diskNumber];
                StringBuffer buffer_data = current_disk.read((file_info.startingSector + i), line);
                printer_manager.all_printers[this.id].print(buffer_data);
            }
        }
        catch (Exception e) 
        { 
            System.out.println(e); 
        }
    }
}

class FileInfo
{
    int diskNumber;
    int startingSector;
    int fileLength;
}

class DirectoryManager
{
    private Hashtable<String, FileInfo> T = new Hashtable<String, FileInfo>();
    DirectoryManager()
    {
    }

    void enter(StringBuffer fileName, FileInfo file)
    {
        T.put(fileName.toString(), file);
    }

    FileInfo lookup(StringBuffer fileName)
    {
        return T.get(fileName.toString());
    }
}

class ResourceManager {
    boolean isFree[];
    ResourceManager(int numberOfItems) 
    {
        isFree = new boolean[numberOfItems];
        for (int i=0; i<isFree.length; ++i)
        {
            isFree[i] = true;
        }          
    }
    synchronized int request() 
    {
        while (true) 
        {
            for (int i = 0; i < isFree.length; ++i)
            {
                if (isFree[i]) 
                {
                    isFree[i] = false;
                    return i;
                }
            }
            try 
            {
                this.wait();
            } 
            catch (InterruptedException e) 
            {
                System.out.println(e);
            }
        }
    }
    synchronized void release(int index) 
    {
        isFree[index] = true;
        this.notify(); 
    }
}
class DiskManager extends ResourceManager
{ 
    DirectoryManager directory;
    Disk all_disks[];
    DiskManager(Disk disks[], int num_disks) 
    {
        super(num_disks);
        this.all_disks = disks;
        this.directory = new DirectoryManager();
    }
}

class PrinterManager extends ResourceManager
{ 
    Printer all_printers[];
    PrinterManager(Printer printers[], int num_printers) 
    {
        super(num_printers);
        this.all_printers = printers;
    }
}

class UserThread extends Thread
{
    int id;
    DiskManager disk_manager;
    PrinterManager printer_manager;
    String file_name;
    UserThread(int id, DiskManager disk_manager, PrinterManager printer_manager)
    {
        this.id = id;
        this.disk_manager = disk_manager;
        this.printer_manager = printer_manager;
        this.file_name = "USER" + Integer.toString(this.id);
    }
    public void run()
    {
        try
        {
            FileInputStream input_stream = null;
            input_stream = new FileInputStream(file_name);
            InputStreamReader stream_reader = new InputStreamReader(input_stream);
            BufferedReader reader = new BufferedReader(stream_reader);
            String current_line = reader.readLine();
            while (current_line != null)
            {
                if (current_line.startsWith(".print"))
                {
                    String file = current_line.substring(7, current_line.length());
                    int current_printer = printer_manager.request();
                    PrintJobThread print_job = new PrintJobThread(disk_manager, printer_manager, file, current_printer);
                    print_job.start();
                    print_job.join();
                    printer_manager.release(current_printer);
                }
                else if (current_line.startsWith(".save"))
                {
                    String file = current_line.substring(6, current_line.length());
                    FileInfo create_file = new FileInfo(); 
                    int current_disk = disk_manager.request();
                    create_file.fileLength = 0;
                    create_file.diskNumber = current_disk;
                    create_file.startingSector = disk_manager.all_disks[current_disk].free_sector_at;
                    current_line = reader.readLine();
                    while (!(current_line).startsWith(".end")) {
                        create_file.fileLength++;
                        disk_manager.all_disks[current_disk].write(disk_manager.all_disks[current_disk].free_sector_at, new StringBuffer(current_line));
                        current_line = reader.readLine();
                    }
                    disk_manager.directory.enter(new StringBuffer(file), create_file);
                    disk_manager.release(current_disk);
                }
                current_line = reader.readLine();
            }
            input_stream.close();
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }
}

class OS141 
{
    int NUM_USERS = 4, NUM_DISKS = 2, NUM_PRINTERS = 3;
    String userFileNames[];
    UserThread users[];
    Disk disks[];
    Printer printers[];
    DiskManager diskManager;
    PrinterManager printerManager;
    static OS141 instance = null;
    OS141() 
    {
    }
    static OS141 instance() 
    {
        if (instance == null) 
        {
            instance = new OS141();
        }
        return instance;
    }
    void configure(String argv[]) 
    { 
        initDisks(argv[1]);
        initPrinters(argv[2]);
        initUsers(argv[0]);
    }
    void initUsers(String num_users)
    {
        NUM_USERS = Math.abs(Integer.parseInt(num_users));
        users = new UserThread[NUM_USERS];
        for(int i = 0; i < NUM_USERS; i++) 
        {
            users[i] = new UserThread(i, diskManager, printerManager);
        }
        startUserThreads();
        joinUserThreads();
    }
    void initDisks(String num_disks)
    {
        NUM_DISKS = Math.abs(Integer.parseInt(num_disks));
        disks = new Disk[NUM_DISKS];
        for(int i = 0; i < NUM_DISKS; i++) 
        {
            disks[i] = new Disk();
        }
        diskManager = new DiskManager(disks, NUM_DISKS);
    }
    void initPrinters(String num_printers)
    {
        NUM_PRINTERS = Math.abs(Integer.parseInt(num_printers)); 
        printers = new Printer[NUM_PRINTERS];
        for(int i = 0; i < NUM_PRINTERS; i++) 
        {
            printers[i] = new Printer(i);
        }
        printerManager = new PrinterManager(printers, NUM_PRINTERS);
        
    }
    void startUserThreads() 
    { 
        for(int i = 0; i < NUM_USERS; i++) 
        {
            users[i].start();
        }
    }
    void joinUserThreads() 
    {
        for(int i = 0; i < NUM_USERS; i++) 
        {
            try 
            {
                users[i].join();
            }
            catch(InterruptedException e) 
            {
                System.out.println(e); 
            }
        }
    }
}

public class MainClass
{
    public static void main(String args[])
    {
        if (args.length != 3)
        {
            System.out.println("ERROR: incorrect number of parameters.");
        }
        else
        {
            OS141 os141 = OS141.instance();
            os141.configure(args);
        }
    }
}