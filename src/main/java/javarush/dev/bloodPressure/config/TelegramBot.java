package javarush.dev.bloodPressure.config;


import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import javarush.dev.bloodPressure.entity.User;
import javarush.dev.bloodPressure.repo.BloodMeasurementRepository;
import javarush.dev.bloodPressure.repo.UserRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.itextpdf.layout.element.Cell;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;


@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final String HELP_TEXT = "This bot is created to demonstrate capabilities \n\n " + "You can execute commands from the main menu \n\n" + "Type /start to see welcome message \n\n" + "Type /mydata to see data stored about yourself \n\n" + "Type /help to see message again \n\n";
    private Map<Long, String> userStates = new HashMap<>();
    private Map<Long, Double> weightCheck = new HashMap<>();
    private Map<Long, User> pendingUsers = new HashMap<>();
    private final BotConfig botConfig;
    private final long defaultAdminChatId = 1003560212;
    private final UserRepository userRepository;
    private final BloodMeasurementRepository bloodMeasurementRepository;
    private Map<Long, String> userTokens = new HashMap<>();
    private static final String FFMPEG_PATH = "C:\\Users\\fbekeshov.DC\\Downloads\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin";
    int counter = 1;
    private Map<Long, String> pendingBloodPressure = new HashMap<>();
    private Map<Long, LocalDateTime> pendingDate = new HashMap<>();


    public TelegramBot(BotConfig botConfig, UserRepository userRepository, BloodMeasurementRepository bloodMeasurementRepository) {
        this.botConfig = botConfig;
        this.userRepository = userRepository;
        this.bloodMeasurementRepository = bloodMeasurementRepository;
        List<BotCommand> listOfCommands = new ArrayList<>();
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            if (pendingUsers.containsKey(chatId)) {
                handlePendingUserRegistration(chatId, message, update.getMessage().getFrom());
            } else if (userStates.containsKey(chatId) && "AWAITING_WEIGHT".equals(userStates.get(chatId))) {
                handleWeightInput(chatId, message);
            } else if (userStates.containsKey(chatId) && "AWAITING_HEIGHT".equals(userStates.get(chatId))) {
                handleHeightInput(chatId, message);
            } else if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long id = update.getCallbackQuery().getMessage().getChatId();
                if ("start_health_checking".equals(callbackData)) {
                    System.out.println("dsadsad");
                }
            }
            else if (update.hasMessage() && update.getMessage().hasText() && userStates.containsKey(update.getMessage().getChatId()) && "AWAITING_START_DATE".equals(userStates.get(update.getMessage().getChatId()))) {
                String startDateString = update.getMessage().getText();
                LocalDateTime startDate = LocalDateTime.parse(startDateString + "T00:00:00");
                sendMessageAnswer(update.getMessage().getChatId(), "Please enter the end date in the format yyyy-MM-dd:");
                userStates.put(update.getMessage().getChatId(), "AWAITING_END_DATE");
                pendingDate.put(update.getMessage().getChatId(), startDate);
            } else if (update.hasMessage() && update.getMessage().hasText() && userStates.containsKey(update.getMessage().getChatId()) && "AWAITING_END_DATE".equals(userStates.get(update.getMessage().getChatId()))) {
                String endDateString = update.getMessage().getText();
                LocalDateTime endDate = LocalDateTime.parse(endDateString + "T23:59:59");
                File pdfFile = generatePDF(update.getMessage().getChatId(), pendingDate.get(update.getMessage().getChatId()), endDate); // Assuming generatePDF returns the byte array of the PDF
                SendDocument sendDocument = new SendDocument();
                sendDocument.setChatId(String.valueOf(update.getMessage().getChatId()));
                sendDocument.setDocument(new InputFile(pdfFile));
                sendDocument.setCaption("Your blood pressure measurements report");

                try {
                    execute(sendDocument);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                userStates.remove(update.getMessage().getChatId());
                pendingDate.remove(update.getMessage().getChatId());

            }
            else {
                if (userStates.containsKey(chatId) && "AWAITING_PRESSURE".equals(userStates.get(chatId))) {
                    handleHealthCheckInput(chatId, message);
                } else {
                    switch (message) {
                        case "/start":
                            startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                            break;
                        case "\uD83D\uDC49 Start Health Checking":
                            log.info("/startHeadlthChecking is pressed with chatId {}", chatId);
                            startHealthChecking(chatId);
                            break;
                        case "/audioBloodPressure":
                        case "\uD83D\uDC49 Audio Blood Pressure":
                            log.info("/audioBloodPressure is pressed with chatId {}", chatId);
                            sendMessageAnswer(update.getMessage().getChatId(), "Please send an audio message with your blood pressure measurement.");
                            break;
                        case "/register":
                        case "register":
                            startRegistration(chatId);
                            break;
                        case "\uD83D\uDC49 Check your BMI":
                            log.info("/checkBMI is pressed with chatId {}", chatId);
                            checkMyIBF(chatId);
                            break;
                        case "\uD83D\uDC49 Show Graphic":
                            log.info("/showGraphic is pressed with chatId {}", chatId);
                            handleShowGraphicCommand(chatId);
                            break;
                        case "\uD83D\uDC49 My measurements":
                            log.info("/mymeasurements is pressed with chatId {}", chatId);
                            getMyPastMeasurements(chatId);
                            break;
                        case "\uD83D\uDC49 Download PDF":
                            log.info("/downloadPDF is pressed with chatId {}", chatId);
                            sendMessageAnswer(chatId, "Please enter the start date in the format yyyy-MM-dd:");
                            userStates.put(chatId, "AWAITING_START_DATE");
                            break;
                        default:
                            handleDefaultMessage(chatId, message);
                            break;
                    }
                }
            }
        } else if (update.hasMessage() && update.getMessage().hasVoice()) {
            long chatId = update.getMessage().getChatId();
            handleAudioMessage(update.getMessage(),chatId);
        }
        else if (update.hasCallbackQuery()) {
                CallbackQuery callbackQuery = update.getCallbackQuery();
                long chatId = callbackQuery.getMessage().getChatId();
                String data = callbackQuery.getData();
                if ("try_again".equals(data)) {
                    log.info("try again  is pressed ");
                    sendMessageAnswer(update.getCallbackQuery().getMessage().getChatId(), "Please send an audio message with your blood pressure measurement.");
                } else if ("yes".equals(data)) {
                    String bloodPressure = pendingBloodPressure.get(chatId);
                    log.info("yes  is pressed ");
                    saveBloodPressureMeasurement(chatId, bloodPressure);
                    pendingBloodPressure.remove(chatId);
                }
            }

        }

    private void saveBloodPressureMeasurement(long chatId, String bloodPressure) {
        Optional<User> user = userRepository.findByChatId(chatId);
        bloodPressure= bloodPressure.replaceAll("by", " ");
        String[] parts = bloodPressure.trim().split("\\s+");  // Use trim() to remove leading and trailing spaces
        if (parts.length == 2) {
            BloodPressureMeasurement bloodPressureMeasurement = new BloodPressureMeasurement();
            bloodPressureMeasurement.setMeasurementTime(LocalDateTime.now());
            String systolic = parts[0];
            String diastolic = parts[1];
            // Now you can save these values to your bloodPressureMeasurement object or repository
            bloodPressureMeasurement.setSystolic(Integer.parseInt(systolic));
            bloodPressureMeasurement.setDiastolic(Integer.parseInt(diastolic));
            bloodPressureMeasurement.setUser(user.get());
            user.get().getBloodPressureMeasurements().add(bloodPressureMeasurement);
            userRepository.save(user.get());
            log.info("bloodPressure is saved with chatId {}", chatId);
            String result = "✅ Your blood pressure was successfully saved.\n Your";

            sendMessageWithCommands(chatId,getBloodMeasurementsResults(bloodPressureMeasurement.getSystolic(), bloodPressureMeasurement.getDiastolic(), user.get(),result) );
        } else {
            sendMessageAnswer(chatId, "❌ Could not recognise, please try again. Please send an audio message with your blood pressure measurement. (e.g., 120 by 80)");

        }

    }


    private void handleAudioMessage(Message message, long chatId) throws IOException, InterruptedException {
        String fileId = message.getVoice().getFileId();
        String savePath = "C:\\Users\\fbekeshov.DC\\Downloads\\audio.ogg";
        downloadAudioFile(fileId, savePath);
        String filePath = String.format("C:\\Users\\fbekeshov.DC\\Downloads\\boomaudio%d.mp3", counter);
        counter++;
        convertAudio(savePath, filePath);

        try (FileInputStream credentialsStream = new FileInputStream("C:\\Users\\fbekeshov.DC\\Downloads\\secure-pottery-424209-a6-e2a94eebebde.json")) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();

            try (SpeechClient speechClient = SpeechClient.create(settings)) {
                // Reads the audio file into memory
                Path path = Paths.get(filePath);
                byte[] data = Files.readAllBytes(path);
                ByteString audioBytes = ByteString.copyFrom(data);

                // Builds the request for speech recognition
                RecognitionConfig config =
                        RecognitionConfig.newBuilder()
                                .setEncoding(RecognitionConfig.AudioEncoding.MP3)
                                .setSampleRateHertz(16000)
                                .setLanguageCode("en-US")
                                .build();
                RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

                RecognizeResponse response = speechClient.recognize(config, audio);
                String recognizedText = response.getResultsList().stream()
                        .map(result -> result.getAlternatives(0).getTranscript())
                        .findFirst()
                        .orElse("");
                if (!recognizedText.isEmpty()) {
                    SendMessage message1 = new SendMessage();
                    message1.setChatId(String.valueOf(chatId));
                    String resultMessage = String.format("\uD83C\uDF99\uFE0F Did I recognize your blood pressure correctly as %s?", recognizedText);
                    message1.setText(resultMessage);
                    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                    List<InlineKeyboardButton> rowInline = new ArrayList<>();
                    InlineKeyboardButton inlineKeyboardButton1 = InlineKeyboardButton.builder()
                            .text("Yes")
                            .callbackData("yes")
                            .build();
                    InlineKeyboardButton inlineKeyboardButton2 = InlineKeyboardButton.builder()
                            .text("Try Again")
                            .callbackData("try_again")
                            .build();

                    rowInline.add(inlineKeyboardButton1);
                    rowInline.add(inlineKeyboardButton2);
                    rowsInline.add(rowInline);
                    markupInline.setKeyboard(rowsInline);
                    message1.setReplyMarkup(markupInline);

                    sendMessage(message1);
                    pendingBloodPressure.put(chatId, recognizedText);
                } else {
                    sendMessageAnswer(chatId, "Sorry, I couldn't recognize your blood pressure. Please try again.");
                }
            }
        }
    }

    private static void convertAudio(String inputFilePath, String outputFilePath) throws IOException, InterruptedException {
        String ffmpegPath = "C:\\Users\\fbekeshov.DC\\Downloads\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin\\ffmpeg.exe";

        // Command to convert audio file to 16000 Hz sample rate
        String[] command = {ffmpegPath, "-i", inputFilePath, "-ar", "16000", outputFilePath};

        // Execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        process.waitFor();
    }

    private String downloadAudioFile(String fileId, String savePath) {
        GetFile getFileRequest = new GetFile();
        getFileRequest.setFileId(fileId);
        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileRequest);

            String filePath = file.getFilePath();
            System.out.println("File path: " + filePath);

            // Construct the download URL
            String downloadUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;
            System.out.println("Download URL: " + downloadUrl);

            // Download the file
            try (InputStream in = new BufferedInputStream(new URL(downloadUrl).openStream());
                 FileOutputStream out = new FileOutputStream(savePath)) {
                byte[] dataBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                    out.write(dataBuffer, 0, bytesRead);
                }
                System.out.println("File downloaded successfully to: " + savePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        return savePath;
    }

    private void handleShowGraphicCommand(long chatId) {
        Optional<User> user = userRepository.findByChatId(chatId);

        if (user.isPresent()) {
            List<BloodPressureMeasurement> measurements = user.get().getBloodPressureMeasurements();
            try {

                File chartFile = createBarChart(measurements);

                SendPhoto sendPhotoRequest = new SendPhoto();
                sendPhotoRequest.setChatId(String.valueOf(chatId));
                sendPhotoRequest.setPhoto(new InputFile(chartFile));
                sendPhotoRequest.setCaption("Here is your blood pressure chart:");
                execute(sendPhotoRequest);
            } catch (IOException | TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            sendMessageAnswer(chatId, "No user found with the given chat ID.");
        }
    }

    private File createBarChart(List<BloodPressureMeasurement> measurements) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        for (BloodPressureMeasurement measurement : measurements) {
            String date = measurement.getMeasurementTime().format(formatter);
            dataset.addValue(measurement.getSystolic(), "Systolic", date);
            dataset.addValue(measurement.getDiastolic(), "Diastolic", date);
        }

        JFreeChart barChart = ChartFactory.createBarChart(
                "Blood Pressure Over Time",
                "Date",
                "Pressure (mmHg)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        CategoryPlot plot = (CategoryPlot) barChart.getPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesPaint(1, Color.BLUE);

        CategoryAxis domainAxis = plot.getDomainAxis();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        File chartFile = new File("BloodPressureChart.png");
        ChartUtils.saveChartAsPNG(chartFile, barChart, 800, 600);

        return chartFile;
    }

    private void getMyPastMeasurements(long chatId) {
        Optional<User> user = userRepository.findByChatId(chatId);
        if (user.isPresent()) {
            List<BloodPressureMeasurement> bloodPressureMeasurements = user.get().getBloodPressureMeasurements();


            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("Your events:\n");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

            for (BloodPressureMeasurement booking : bloodPressureMeasurements) {
                String formattedDateTime = booking.getMeasurementTime().format(formatter);
                String formattedPressure = String.valueOf(" " + booking.getSystolic() + "/" + booking.getDiastolic());
                String message = " ";
                message = getBloodMeasurementsResults(booking.getSystolic(), booking.getDiastolic(), user.get(), message);
                String buttonText = "Date: " + formattedDateTime + ", " + formattedPressure + message;
                String callbackData = "getEvent" + booking.getId();
                InlineKeyboardButton button = InlineKeyboardButton.builder().text(buttonText).callbackData(callbackData).build();
                List<InlineKeyboardButton> row = new ArrayList<>();
                row.add(button);
                keyboard.add(row);
            }
            keyboardMarkup.setKeyboard(keyboard);
            if (bloodPressureMeasurements.isEmpty()) {
                SendMessage message = new SendMessage(String.valueOf(chatId), "You dont have any measurements yet ");
                message.setReplyMarkup(keyboardMarkup);
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                SendMessage message = new SendMessage(String.valueOf(chatId), "Select an event:");
                message.setReplyMarkup(keyboardMarkup);
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private File generatePDF(long chatId, LocalDateTime startDate, LocalDateTime endDate) {
        File outputFile = new File("BloodPressureMeasurements_" + chatId + ".pdf");

        Optional<User> userOptional = userRepository.findByChatId(chatId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            List<BloodPressureMeasurement> measurements = bloodMeasurementRepository.findByUserAndMeasurementTimeBetween(user, startDate, endDate);
            if (!measurements.isEmpty()) {
                try (PdfWriter writer = new PdfWriter(outputFile);
                     PdfDocument pdf = new PdfDocument(writer);
                     Document document = new Document(pdf, PageSize.A4)) {

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    document.add(new Paragraph("User Information")
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());

                    document.add(new Paragraph("Name: " + user.getFirstName() + " " + user.getLastName())
                            .setFont(font)
                            .setFontSize(12));

                    document.add(new Paragraph("Age: " + user.getAge())
                            .setFont(font)
                            .setFontSize(12));

                    document.add(new Paragraph("\nBlood Pressure Measurements")
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());
                    double avgDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).average().orElse(0);
                    double avgSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).average().orElse(0);
                    int maxSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).max().orElse(0);
                    int minSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).min().orElse(0);
                    int maxDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).max().orElse(0);
                    int minDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).min().orElse(0);
                    document.add(new Paragraph("\nAvergae blood measurement from : " + startDate + " to " + endDate)
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());
                    String messageText = String.format(
                            "Here are your  blood pressure stats:\n" +
                                    "Average Systolic: %.2f\n" +
                                    "Average Diastolic: %.2f\n" +
                                    "Max Systolic: %d\n" +
                                    "Min Systolic: %d\n" +
                                    "Difference (Systolic): %d\n" +
                                    "Max Diastolic: %d\n" +
                                    "Min Diastolic: %d\n" +
                                    "Difference (Diastolic): %d",
                            avgSystolic, avgDiastolic, maxSystolic, minSystolic, (maxSystolic - minSystolic), maxDiastolic, minDiastolic, (maxDiastolic - minDiastolic)
                    );
                    document.add(new Paragraph(messageText)
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());


                    Table table = new Table(3);

                    // Add table headers
                    table.addCell(createCell("Date", font, true));
                    table.addCell(createCell("Systolic", font, true));
                    table.addCell(createCell("Diastolic", font, true));

                    // Add table rows
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                    for (BloodPressureMeasurement measurement : measurements) {
                        table.addCell(createCell(measurement.getMeasurementTime().format(formatter), font, false));
                        table.addCell(createCell(String.valueOf(measurement.getSystolic()), font, false));
                        table.addCell(createCell(String.valueOf(measurement.getDiastolic()), font, false));
                    }

                    document.add(table);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try (PdfWriter writer = new PdfWriter(outputFile);
                     PdfDocument pdf = new PdfDocument(writer);
                     Document document = new Document(pdf, PageSize.A4)) {

                    PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                    document.add(new Paragraph("User Information")
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());

                    document.add(new Paragraph("Name: " + user.getFirstName() + " " + user.getLastName())
                            .setFont(font)
                            .setFontSize(12));

                    document.add(new Paragraph("Age: " + user.getAge())
                            .setFont(font)
                            .setFontSize(12));

                    document.add(new Paragraph("\nBlood Pressure Measurements")
                            .setFont(font)
                            .setFontSize(14)
                            .setBold());


                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        return outputFile;
    }

    private Cell createCell(String content, PdfFont font, boolean isHeader) {
        Cell cell = new Cell();
        Paragraph paragraph = new Paragraph(content).setFont(font);
        if (isHeader) {
            paragraph.setBold().setBackgroundColor(new DeviceRgb(200, 200, 200));
        }
        cell.add(paragraph);
        cell.setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        return cell;
    }

    private void checkMyIBF(long chatId) {
        Optional<User> userOptional = userRepository.findByChatId(chatId);
        if (isUserRegistered(chatId)) {
            log.info("checkMyIBF");
            sendMessageAnswer(chatId, "⚖\uFE0F Please enter your weight in kilograms (kg):");
            userStates.put(chatId, "AWAITING_WEIGHT");
        } else {
            sendMessageAnswer(chatId, "Oops! You need to register first. Please use the /register command.");
        }
    }

    private void handleWeightInput(long chatId, String message) {
        try {
            double weight = Double.parseDouble(message);
            userStates.put(chatId, "AWAITING_HEIGHT");
            weightCheck.put(chatId, weight);
            sendMessageAnswer(chatId, "\uD83C\uDFE5 Thank you! Now, please enter your height in meters (e.g. , 1.80 ).");
        } catch (NumberFormatException e) {
            sendMessageAnswer(chatId, "❌ Invalid format. Please enter your weight in kilograms (e.g., 70.5 ).");
        }
    }

    private void handleHeightInput(long chatId, String message) {
        try {
            double height = Double.parseDouble(message);
            double weight = weightCheck.remove(chatId);
            double bmi = calculateBMI(weight, height);
            userStates.remove(chatId);

            String resultMessage = "\uD83D\uDCCA Your BMI is " + String.format("%.2f", bmi) + ". ";

            if (bmi < 18.5) {
                resultMessage += "You are underweight.";
            } else if (bmi < 24.9) {
                resultMessage += "You have a normal weight.";
            } else if (bmi < 29.9) {
                resultMessage += "You are overweight.";
            } else {
                resultMessage += "You are obese.";
            }

            sendMessageWithCommands(chatId, resultMessage);
        } catch (NumberFormatException e) {
            sendMessageAnswer(chatId, "❌ Invalid format. Please enter your height in meters (e.g., 1.75).");
        }
    }

    private double calculateBMI(double weight, double height) {
        return weight / (height * height);
    }

    private void handleHealthCheckInput(long chatId, String message) {
        Optional<User> userOptional = userRepository.findByChatId(chatId);
        String[] parts = message.split("/");
        if (parts.length == 2) {
            try {
                int systolic = Integer.parseInt(parts[0]);
                int diastolic = Integer.parseInt(parts[1]);
                BloodPressureMeasurement bloodPressureMeasurement = new BloodPressureMeasurement();
                bloodPressureMeasurement.setDiastolic(diastolic);
                bloodPressureMeasurement.setSystolic(systolic);
                bloodPressureMeasurement.setMeasurementTime(LocalDateTime.now());
                bloodPressureMeasurement.setUser(userOptional.get());

                userOptional.get().getBloodPressureMeasurements().add(bloodPressureMeasurement);
                userRepository.save(userOptional.get());
                userStates.remove(chatId);
                String resultMessage = "Thank you! Your pressure has been recorded: Systolic=" + systolic + ", Diastolic=" + diastolic + "\n";
                resultMessage = getBloodMeasurementsResults(systolic, diastolic, userOptional.get(), resultMessage);

                sendMessageWithCommands(chatId, resultMessage);

            } catch (NumberFormatException e) {
                sendMessageAnswer(chatId, "❌ Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
            }
        } else {
            sendMessageAnswer(chatId, "❌ Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
        }
    }

    private String getBloodMeasurementsResults(int systolic, int diastolic, User user, String resultMessage) {

        if (systolic < 90 || diastolic < 60) {
            resultMessage = "⚠\uFE0F Blood pressure is low .";
        } else if ((systolic >= 90 && systolic <= 120) && (diastolic >= 60 && diastolic <= 80)) {
            resultMessage = "✔\uFE0F Blood pressure is normal.";
        } else if ((systolic > 120 && systolic <= 129) &&( diastolic < 85) ){
            resultMessage = "Blood pressure is elevated.";
        } else if ((systolic >= 130 && systolic <= 139) || (diastolic >= 80 && diastolic <= 89) ){
            resultMessage = "Blood pressure is increased . Contact your doctor ! ";
        } else if (systolic >= 140 || diastolic >= 90) {
            resultMessage = "Blood pressure is increased . Contact your doctor ! ";
        } else if (systolic > 180 || diastolic > 120) {
            resultMessage = "Blood pressure  hypertensive crisis. Seek emergency care immediately!";
        }

        return resultMessage;
    }

    private void handleDefaultMessage(long chatId, String message) {
        if (userTokens.containsKey(chatId)) {
            String token = userTokens.get(chatId);
            if (message.equals(token)) {
                log.info("Authentication is successful  with chatId {}", chatId);
                sendMessageAnswer(chatId, "Authentication successful!");
            } else {
                log.info("Authentication is failed  with chatId {}", chatId);
                sendMessageAnswer(chatId, "Authentication failed. Please try again.");
            }
            userTokens.remove(chatId);
        } else {
            sendMessageAnswer(chatId, "⛔ Sorry, command was not recognized.");
        }
    }

    private void startRegistration(long chatId) {
        if (userRepository.findByChatId(chatId).isPresent()) {
            log.info("User is already registered with chatId {}", chatId);
            sendMessageWithCommands(chatId, "You are already registered");
        }
        User user = new User();
        user.setChatId(chatId);
        pendingUsers.put(chatId, user);
        sendMessageAnswer(chatId, "\uD83D\uDCDD Welcome! Let's get you registered. Please enter your first name: ");
    }

    private void handlePendingUserRegistration(long chatId, String message, org.telegram.telegrambots.meta.api.objects.User from) {
        log.info("User registration is started with chatId {}", chatId);
        User user = pendingUsers.get(chatId);
        if (user.getFirstName() == null) {
            user.setFirstName(message);
            sendMessageAnswer(chatId,  " \uD83D\uDCDD Please enter your last name:");
        } else if (user.getLastName() == null) {
            user.setLastName(message);
            sendMessageAnswer(chatId, "\uD83D\uDC68\u200D\uD83D\uDCBB Please enter your email:");
        } else if (user.getEmail() == null) {
            user.setEmail(message);
            sendMessageAnswer(chatId, "Please enter age:");
        } else if (user.getAge() == null) {
            try {
                int age = Integer.parseInt(message);
                user.setAge(age);
                sendMessageAnswer(chatId, "Which kind of blood pressure medicine are you taking?");
            } catch (NumberFormatException e) {
                sendMessageAnswer(chatId, "Please enter a valid number for age:");
            }
        } else if (user.getMedication() == null) {
            user.setMedication(message);
            sendMessageAnswer(chatId, "Please enter the time you take the medicine (e.g., 08:00 or 20:00):");
        } else if (user.getMedicineTime() == null) {
            user.setMedicineTime(message);
            user.setUsername(from.getUserName());
            userRepository.save(user);
            pendingUsers.remove(chatId);
            String message1 = "✅ Registration complete! You can now use the bot ";
            sendMessageWithCommands(chatId, message1);
        }
    }


    private void startHealthChecking(long chatId) {
        if (isUserRegistered(chatId)) {
            sendMessageAnswer(chatId, "Please enter your Ciastolic and Diastolic pressure: (e.g - 120/80)");
            userStates.put(chatId, "AWAITING_PRESSURE");
        } else {
            log.info("User isnot  registered with chatId {} to start HealthChecking", chatId);
            sendMessageAnswer(chatId, "Ooops! You need to register first. Please use the /register command.");
        }
    }

    private boolean isUserRegistered(long chatId) {
        Optional<User> user = userRepository.findByChatId(chatId);
        log.info("User is registered with chatId {}", chatId);
        return user.isPresent();
    }

    private void sendMessageAnswer(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getStackTrace().toString());
        }
    }

    private void startCommandReceived(long chatId, String firstName) {
        log.info("/start command is pressed with chatId {}", chatId);
        String botUsername = getBotUsername();
        String answer = "Hi, " + firstName + "! My name is " + botUsername + ".\n\n" +
                "\uD83D\uDCCB Here are some commands to get started: ";
        sendMessageWithCommands(chatId, answer);
    }

    public void sendMessageWithCommands(long chatId, String resultMessage) {
        log.info("sendMessageWithCommands is pressed with chatId {}", chatId);

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(resultMessage);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // First row
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("\uD83D\uDC49 Start Health Checking"));
        row.add(new KeyboardButton("\uD83D\uDC49 My measurements"));
        keyboard.add(row);
        row = new KeyboardRow();
        row.add(new KeyboardButton("\uD83D\uDC49 Check your BMI"));
        row.add(new KeyboardButton("\uD83D\uDC49 Download PDF"));
        keyboard.add(row);

        // Second row
        row = new KeyboardRow();
        row.add(new KeyboardButton("\uD83D\uDC49 Show Graphic"));
        row.add(new KeyboardButton("\uD83D\uDC49 Audio Blood Pressure"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);

        message.setReplyMarkup(keyboardMarkup);

        sendMessage(message);
    }

    public void sendMessage(SendMessage message) {
        log.info("sendMessage is pressed with chatId {}", message.getChatId());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }
}
