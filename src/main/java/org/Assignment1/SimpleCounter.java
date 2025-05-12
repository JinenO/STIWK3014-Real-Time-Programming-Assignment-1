package org.Assignment1;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleCounter {

    private static final AtomicInteger totalIssueCount= new AtomicInteger(0);//to get total issue counted
    private static final AtomicInteger nextFileIndex= new AtomicInteger(0);//to get the next file index for the issue counting process
    private static final AtomicInteger totalJavaFilesCount= new AtomicInteger(0);//to get number of total java file
    private static final List<File> allJavaFiles= new ArrayList<>();

    public static void main (String [] args){
        Scanner scan= new Scanner(System.in);

        System.out.println("Enter the directory path to the file:");
        String path=scan.nextLine().trim();

        File dir=new File(path);
        while(!dir.exists()||!dir.isDirectory()){
            System.out.println("This specified path is not a valid directory");
            System.out.println("Enter the directory path to the file (or type 'ESC' to exit system):");
            path=scan.nextLine().trim();
            dir=new File(path);
            if(path.equalsIgnoreCase("ESC")){
                /*solve the problem that user don't have
                correct directory but stuck in the cycle*/
                System.out.println("System exit by user");
                System.exit(0);
            }
        }

        System.out.println("Enter the number of threads (should be more than 0):");
        int threadcount=3;
        try{
            threadcount=Integer.parseInt(scan.nextLine().trim());
            if(threadcount<1){//if user input value less than 1 let the threadcount be 3
                threadcount=3;
                System.out.println("Using default: 3 thread");
            }
        }catch(NumberFormatException e){
            System.out.println("Number of threads must be an integer & more than 0");
            System.out.println("Using default: 3 thread");
        }

        findJavaFiles(dir, threadcount);//solved the problem of count java file
        System.out.println("\nTotal number of Java files: "+allJavaFiles.size()+"\n");

        System.out.println("List of issues:");

        List<WorkerThread> threads= new ArrayList<>();
        if(!allJavaFiles.isEmpty()){
            for(int i=0;i<threadcount;i++){
                WorkerThread thread= new WorkerThread("Worker-"+i);
                threads.add(thread);
                thread.start();//start the thread to complete the process of counting number of issues
            }
        }

        for(WorkerThread thread: threads){
            try{
                thread.join();//to let one thread start only when the thread before end to prevent duplication
            } catch (InterruptedException e) {
                System.out.println("Thread was interrupted");
                Thread.currentThread().interrupt();
            }
        }

        if(totalIssueCount.get()==0){
            System.out.println("No issues founded");
        }
        System.out.println("\nTotal number of Issues: "+totalIssueCount.get());
    }

    private static void findJavaFiles(File dir, int threadcount){
        System.out.println("\nList of Java files:");
        List<File> directories=new ArrayList<>();
        collectDirectories(dir, directories);

        ExecutorService executor= Executors.newFixedThreadPool(threadcount);
        //create thread pool with threadcount of thread

        for (File file : directories) {
            executor.submit(()->{
                File[] files=file.listFiles();
                if(files!=null){
                    for(File f:files){
                        if(f.isFile() && f.getName().endsWith(".java")){
                            synchronized (allJavaFiles){
                                allJavaFiles.add(f);
                            }
                            totalJavaFilesCount.incrementAndGet();
                            System.out.println(Thread.currentThread().getName()+" found "+f.getName());
                        }
                    }
                }

            });
        }
        executor.shutdown();
        try{
            //noinspection ResultOfMethodCallIgnored
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for threads to finish");
            Thread.currentThread().interrupt();
        }
        if(allJavaFiles.isEmpty()){
            System.out.println("-");
        }
    }

    private static void collectDirectories(File dir, List<File> directories){
        directories.add(dir);
        File[] files=dir.listFiles();
        if(files!=null){
            for(File f:files){
                if(f.isDirectory()){
                    collectDirectories(f, directories);
                }
            }
        }
    }

    private static class WorkerThread extends Thread{
        private int localIssueCount=0;

        public WorkerThread(String name){
            super(name);
        }

        @Override
        public void run(){
            while(true){
                int fileIndex=nextFileIndex.getAndIncrement();
                if(fileIndex>=allJavaFiles.size()){
                    break;
                }

                File file;
                synchronized (allJavaFiles){
                    file=allJavaFiles.get(fileIndex);
                }

                try{
                    int issue=countIssuesInFile(file);
                    localIssueCount+=issue;

                    if(issue>0){
                        System.out.println(getName()+" found "+issue+" issues in "+file.getName());
                    }
                }catch (IOException e){
                    System.out.println("Error processing file "+file.getName());
                }
            }
            totalIssueCount.addAndGet(localIssueCount);
        }

        private int countIssuesInFile(File file) throws IOException{
            int count=0;
            boolean inComment=false;
            try(BufferedReader br=new BufferedReader(new FileReader(file))){
                String line;
                while((line=br.readLine())!=null){
                    String trimmedLine=line.trim().toLowerCase();

                    if(trimmedLine.contains("/*")){
                        inComment=true;
                    }

                    if(trimmedLine.contains("//")||inComment){
                        if(containIssueKeyword(trimmedLine)){
                            count++;
                        }
                    }

                    if(trimmedLine.contains("*/")){
                        inComment=false;
                    }
                }
            }
            return count;
        }

        private boolean containIssueKeyword(String line){
            return(line.contains("fix")||
                    line.contains("solve")||
                    line.contains("done"))||
                    line.contains("complete");
        }
    }
}

