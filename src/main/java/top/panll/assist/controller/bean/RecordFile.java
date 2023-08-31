package top.panll.assist.controller.bean;

public class RecordFile {
    private String app;
    private String stream;

    private String fileName;

    private String date;


    public static RecordFile instance(String app, String stream, String fileName, String date) {
        RecordFile recordFile = new RecordFile();
        recordFile.setApp(app);
        recordFile.setStream(stream);
        recordFile.setFileName(fileName);
        recordFile.setDate(date);
        return recordFile;
    }


    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
