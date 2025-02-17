package tigerworkshop.webapphardwarebridge.websocketservices;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import tigerworkshop.webapphardwarebridge.dtos.Config;
import tigerworkshop.webapphardwarebridge.dtos.NotificationDTO;
import tigerworkshop.webapphardwarebridge.interfaces.WebSocketServerInterface;
import tigerworkshop.webapphardwarebridge.interfaces.WebSocketServiceInterface;
import tigerworkshop.webapphardwarebridge.responses.PrintDocument;
import tigerworkshop.webapphardwarebridge.responses.PrintResult;
import tigerworkshop.webapphardwarebridge.services.ConfigService;
import tigerworkshop.webapphardwarebridge.services.DocumentService;
import tigerworkshop.webapphardwarebridge.utils.AnnotatedPrintable;
import tigerworkshop.webapphardwarebridge.utils.ImagePrintable;
import tigerworkshop.webapphardwarebridge.dtos.Config.PrinterMapping;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import javax.print.*;
import java.awt.*;
import java.awt.print.*;
import java.io.File;
import java.util.Optional;

@Log4j2
public class PrinterWebSocketService implements WebSocketServiceInterface {
    private WebSocketServerInterface server;

    private static final ConfigService configService = ConfigService.getInstance();
    private static final DocumentService documentService = DocumentService.getInstance();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final float A4_WIDTH_MM = 210.0f;
    private static final float A4_HEIGHT_MM = 297.0f;
    private static final float CONVERSION_FACTOR = 0.3528f;
    private static final double MM_TO_POINTS = 2.8346;

    public PrinterWebSocketService() {
        log.info("Starting PrinterWebSocketService");
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void messageToService(String message) {
        try {
            PrintDocument printDocument = objectMapper.readValue(message, PrintDocument.class);
            printDocument(printDocument);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void messageToService(byte[] message) {
        log.error("PrinterWebSocketService onDataReceived: binary data not supported");
    }

    @Override
    public void onRegister(WebSocketServerInterface server) {
        this.server = server;
    }

    @Override
    public void onUnregister() {
        this.server = null;
    }

    @Override
    public String getChannel() {
        return "/printer";
    }

    /**
     * Prints a PrintDocument
     */
    public void printDocument(PrintDocument printDocument) throws Exception {
        log.info("Printing Document {}, {}", printDocument.getType(), printDocument.getUrl());

        PrinterSearchResult printerSearchResult = null;
        try {
            printerSearchResult = searchPrinterForType(printDocument.getType());

            server.messageToService("/notification", objectMapper.writeValueAsString(new NotificationDTO("INFO", "Printing " + printDocument.getType(), printDocument.getUrl())));

            if (isRaw(printDocument)) {
                printRaw(printDocument, printerSearchResult);
            } else if (isImage(printDocument)) {
                printImage(printDocument, printerSearchResult);
            } else if (isPDF(printDocument)) {
                printPDF(printDocument, printerSearchResult);
            } else {
                throw new Exception("Unknown file type: " + printDocument.getUrl());
            }

            server.messageToServer(getChannel(), objectMapper.writeValueAsString(new PrintResult(true, "Success", printDocument.getId(), printerSearchResult.getName())));
        } catch (Exception e) {
            String errorMessage = e.getMessage();

            if (e instanceof PrinterAbortException) {
                errorMessage = "Printing aborted";
            }

            log.error("Print Error: {}, {}", e.getClass().getName(), errorMessage);

            if (!isRaw(printDocument)) {
                log.error("Print Error: Deleting downloaded document");
                documentService.deleteDocument(printDocument);
            }

            server.messageToService("/notification", objectMapper.writeValueAsString(new NotificationDTO("ERROR", "Print Error " + printDocument.getType(), errorMessage)));

            server.messageToServer(getChannel(), objectMapper.writeValueAsString(new PrintResult(false, errorMessage, printDocument.getId(), printerSearchResult != null ? printerSearchResult.getName() : null)));
        }
    }

    /**
     * Return if PrintDocument is raw
     */
    private Boolean isRaw(PrintDocument printDocument) {
        return printDocument.getRawContent() != null && !printDocument.getRawContent().isEmpty();
    }

    /**
     * Return if PrintDocument is image
     */
    private Boolean isImage(PrintDocument printDocument) {
        String filename = FilenameUtils.getName(printDocument.getUrl());

        return filename.matches("^.*\\.(jpg|jpeg|png|gif)$");
    }

    /**
     * Return if PrintDocument is PDF
     */
    private Boolean isPDF(PrintDocument printDocument) {
        String filename = FilenameUtils.getName(printDocument.getUrl());

        return filename.matches("^.*\\.(pdf)$");
    }

    /**
     * Prints raw bytes to specified printer.
     */
    private void printRaw(PrintDocument printDocument, PrinterSearchResult printerSearchResult) throws PrintException {
        log.debug("printRaw::{}", printDocument);
        long timeStart = System.currentTimeMillis();

        byte[] bytes = Base64.decodeBase64(printDocument.getRawContent());

        DocPrintJob docPrintJob = printerSearchResult.getDocPrintJob();
        Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        docPrintJob.print(doc, null);

        long timeFinish = System.currentTimeMillis();
        log.info("printRaw finished in {} ms", timeFinish - timeStart);
    }

    /**
     * Prints image to specified printer.
     */
    private void printImage(PrintDocument printDocument, PrinterSearchResult printerSearchResult) throws Exception {
        log.debug("printImage::{}", printDocument);

        File file = documentService.prepareDocument(printDocument);
        String path = file.getPath();
        String filename = file.getName();

        long timeStart = System.currentTimeMillis();

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(printerSearchResult.getDocPrintJob().getPrintService());

        PageFormat pageFormat = getPageFormat(job, printerSearchResult);

        Image image = ImageIO.read(new File(path));

        Book book = new Book();
        AnnotatedPrintable printable = new AnnotatedPrintable(new ImagePrintable(image));

        for (AnnotatedPrintable.AnnotatedPrintableAnnotation printDocumentExtra : printDocument.getExtras()) {
            printable.addAnnotation(printDocumentExtra);
        }

        book.append(printable, pageFormat);

        job.setPageable(book);
        job.setJobName(filename);
        job.setCopies(printDocument.getQty());
        job.print();

        long timeFinish = System.currentTimeMillis();

        log.info("printImage {} finished in {} ms", filename, timeFinish - timeStart);
    }

    /**
     * Prints PDF to specified printer.
     */
    private void printPDF(PrintDocument printDocument, PrinterSearchResult printerSearchResult) throws Exception {
        log.info("printPDF started for printer: {}", printerSearchResult.getName());

        File file = documentService.prepareDocument(printDocument);
        String path = file.getPath();
        String filename = file.getName();
        String printerName = printerSearchResult.getName();

        PrinterMapping mapping = printerSearchResult.getMapping();
        if (mapping == null) {
            return;
        }
        
        String customLprCommand = mapping.getCustomLprCommand();
        if (customLprCommand != null && !customLprCommand.isEmpty()) {
            String processedCommand = customLprCommand
                .replace("{printer}", printerName)
                .replace("{file}", path)
                .replace("{paperSize}", mapping.getPaperSize());
                
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", processedCommand);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("LPR Print job successfully sent to printer: {}", printerName);
            } else {
                log.error("LPR Print job failed for printer: {}", printerName);
            }
            return;
        }

        DocPrintJob docPrintJob = printerSearchResult.getDocPrintJob();
        if (docPrintJob == null) {
            return;
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(docPrintJob.getPrintService());
        
        PageFormat pageFormat = getPageFormat(job, printerSearchResult);
        Paper paper = pageFormat.getPaper();

        try (PDDocument document = PDDocument.load(file)) {
            Book book = new Book();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                PageFormat eachPageFormat = (PageFormat) pageFormat.clone();
                
                if (mapping.isAutoRotate()) {
                    PDRectangle cropBox = document.getPage(i).getCropBox();
                    if (cropBox.getWidth() > cropBox.getHeight()) {
                        log.debug("Auto rotation: LANDSCAPE");
                        eachPageFormat.setOrientation(PageFormat.LANDSCAPE);
                    } else {
                        log.debug("Auto rotation: PORTRAIT");
                        eachPageFormat.setOrientation(PageFormat.PORTRAIT);
                    }
                }
                
                PDFPrintable pdfPrintable = new PDFPrintable(document, Scaling.ACTUAL_SIZE, false, mapping.getForceDPI());
                AnnotatedPrintable annotatedPrintable = new AnnotatedPrintable(pdfPrintable);
                for (AnnotatedPrintable.AnnotatedPrintableAnnotation annotation : printDocument.getExtras()) {
                    annotatedPrintable.addAnnotation(annotation);
                }
                book.append(annotatedPrintable, eachPageFormat);
            }
            job.setPageable(book);
            job.setJobName(filename);
            job.setCopies(printDocument.getQty());
            job.print();
            log.info("Print job successfully sent to printer: {}", printerName);

        } catch (Exception e) {
            log.error("printPDF Error: {}", e.getMessage(), e);
        }
    }


    /**
     * Get PrinterSearchResult for specified type
     */
    private PrinterSearchResult searchPrinterForType(String type) throws PrinterException {
        Optional<Config.PrinterMapping> printerMappingOptional = configService.getConfig().getPrinter().getMappings().stream().filter(it -> it.getType().equals(type)).findFirst();

        if (printerMappingOptional.isPresent()) {
            Config.PrinterMapping printerMapping = printerMappingOptional.get();
            PrintService[] printServices = PrinterJob.lookupPrintServices();

            for (PrintService printService : printServices) {
                if (printService.getName().equalsIgnoreCase(printerMapping.getName())) {
                    log.info("Sending print job type: {} to printer: {}", type, printService.getName());

                    return new PrinterSearchResult(printService.getName(), printerMapping, printService.createPrintJob(), false);
                }
            }
        }

         if (configService.getConfig().getPrinter().isAutoAddUnknownType()) {
             // Add unknown type does not already exist
             if (configService.getConfig().getPrinter().getMappings().stream().noneMatch(it -> it.getType().equals(type))) {
                 configService.addPrintTypeToList(type);
             }
        }

         if (configService.getConfig().getPrinter().isFallbackToDefault()) {
             log.info("No mapped print job type: {}, falling back to default printer", type);

             PrintService printService = PrintServiceLookup.lookupDefaultPrintService();

             if (printService == null) {
                 throw new PrinterException("No default printer found");
             }

             return new PrinterSearchResult(printService.getName(), new Config.PrinterMapping(), printService.createPrintJob(), true);
        }

         throw new PrinterException("No matched printer: " + type);
    }

    private PageFormat getPageFormat(PrinterJob job, PrinterSearchResult printerSearchResult) {
        PageFormat pageFormat = job.defaultPage();
        Paper paper = new Paper();
        PrinterMapping mapping = printerSearchResult.getMapping();
        String paperSize = mapping.getPaperSize();

        if (paperSize != null && !paperSize.isEmpty()) {
            if (paperSize.startsWith("Custom.")) {
                try {
                    String[] dimensions = paperSize.replace("Custom.", "").replace("mm", "").split("x");
                    double width = Double.parseDouble(dimensions[0]) * MM_TO_POINTS;
                    double height = Double.parseDouble(dimensions[1]) * MM_TO_POINTS;
                    paper.setSize(width, height);
                    paper.setImageableArea(0, 0, width, height);
                } catch (Exception e) {
                    log.error("Invalid paper size format in mapping: {}", paperSize, e);
                }
            } else if (paperSize.equalsIgnoreCase("A4") || paperSize.equalsIgnoreCase("iso_a4_210x297mm")) {
                paper.setSize(A4_WIDTH_MM * MM_TO_POINTS, A4_HEIGHT_MM * MM_TO_POINTS);
                paper.setImageableArea(0, 0, A4_WIDTH_MM * MM_TO_POINTS, A4_HEIGHT_MM * MM_TO_POINTS);
            }
        } else {
            paper.setSize(A4_WIDTH_MM * MM_TO_POINTS, A4_HEIGHT_MM * MM_TO_POINTS);
            paper.setImageableArea(0, 0, A4_WIDTH_MM * MM_TO_POINTS, A4_HEIGHT_MM * MM_TO_POINTS);
        }
        pageFormat.setPaper(paper);
        return pageFormat;
    }


    @Getter
    @AllArgsConstructor
    private static class PrinterSearchResult {
        private String name;
        private Config.PrinterMapping mapping;
        private DocPrintJob docPrintJob;
        private Boolean isDefault;
    }
}