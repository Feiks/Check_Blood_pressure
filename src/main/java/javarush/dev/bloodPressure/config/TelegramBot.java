package javarush.dev.bloodPressure.config;


import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.resources.transcripts.types.Transcript;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import javarush.dev.bloodPressure.entity.User;
import javarush.dev.bloodPressure.repo.BloodMeasurementRepository;
import javarush.dev.bloodPressure.repo.UserRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
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
          else {
                if (userStates.containsKey(chatId) && "AWAITING_PRESSURE".equals(userStates.get(chatId))) {
                    handleHealthCheckInput(chatId, message);
                } else {
                    switch (message) {
                        case "/start":
                            startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                            break;
                        case "Start Health Checking":
                            startHealthChecking(chatId);
                            break;
                        case "/audioBloodPressure":
                            sendMessageAnswer(update.getMessage().getChatId(), "Please send an audio message with your blood pressure measurement.");
                            break;
                        case "/register":
                        case "register":
                            startRegistration(chatId);
                            break;
                        case "Check your BMI":
                            checkMyIBF(chatId);
                            break;
                        case "Show Graphic":
                            handleShowGraphicCommand(chatId);
                            break;
                        case "My measurements":
                            getMyPastMeasurements(chatId);
                            break;
                        default:
                            handleDefaultMessage(chatId, message);
                            break;
                    }
                }
            }
        }
        else if (update.hasMessage() && update.getMessage().hasVoice()) {
            handleAudioMessage(update.getMessage());
        }

    }

    private void handleAudioMessage(Message message) throws IOException, InterruptedException {
        String fileId = message.getVoice().getFileId();
        String savePath = "C:\\Users\\fbekeshov.DC\\Downloads\\audio.ogg";
        downloadAudioFile(fileId,savePath);
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
                for (SpeechRecognitionResult result : response.getResultsList()) {
                    System.out.println(result.getAlternatives(0).getTranscript());
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

    private void checkMyIBF(long chatId) {
        Optional<User> userOptional = userRepository.findByChatId(chatId);
        if (isUserRegistered(chatId)) {
            log.info("checkMyIBF");
            sendMessageAnswer(chatId, "Please enter your weight in kilograms (kg):");
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
            sendMessageAnswer(chatId, "Thank you! Now, please enter your height in meters (e.g. , 1.80 ).");
        } catch (NumberFormatException e) {
            sendMessageAnswer(chatId, "Invalid format. Please enter your weight in kilograms (e.g., 70.5 ).");
        }
    }

    private void handleHeightInput(long chatId, String message) {
        try {
            double height = Double.parseDouble(message);
            double weight = weightCheck.remove(chatId);
            double bmi = calculateBMI(weight, height);
            userStates.remove(chatId);

            String resultMessage = "Your BMI is " + String.format("%.2f", bmi) + ". ";

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
            sendMessageAnswer(chatId, "Invalid format. Please enter your height in meters (e.g., 1.75).");
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

                userOptional.get().getBloodPressureMeasurements().add(bloodPressureMeasurement);
                userRepository.save(userOptional.get());
                userStates.remove(chatId);
                String resultMessage = "Thank you! Your pressure has been recorded: Systolic=" + systolic + ", Diastolic=" + diastolic + "\n";
                resultMessage = getBloodMeasurementsResults(systolic, diastolic, userOptional.get(), resultMessage);

                sendMessageWithCommands(chatId, resultMessage);

            } catch (NumberFormatException e) {
                sendMessageAnswer(chatId, "Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
            }
        } else {
            sendMessageAnswer(chatId, "Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
        }
    }

    private String getBloodMeasurementsResults(int systolic, int diastolic, User user, String resultMessage) {

        if (systolic < 90 || diastolic < 60) {
            resultMessage += "Blood pressure is low.";
        } else if (systolic <= 129 && diastolic <= 89) {
            resultMessage += "Blood pressure is normal.";
        } else {
            resultMessage += "Blood pressure is high. You should take your pills " + user.getMedication();
        }
        return resultMessage;
    }

    private void handleDefaultMessage(long chatId, String message) {
        if (userTokens.containsKey(chatId)) {
            String token = userTokens.get(chatId);
            if (message.equals(token)) {
                sendMessageAnswer(chatId, "Authentication successful!");
            } else {
                sendMessageAnswer(chatId, "Authentication failed. Please try again.");
            }
            userTokens.remove(chatId);
        } else {
            sendMessageAnswer(chatId, "Sorry, command is not recognized. 🤔");
        }
    }

    private void startRegistration(long chatId) {
        if (userRepository.findByChatId(chatId).isPresent()) {
            sendMessageWithCommands(chatId, "You are already registered");
        }
        User user = new User();
        user.setChatId(chatId);
        pendingUsers.put(chatId, user);
        sendMessageAnswer(chatId, "Welcome! Let's get you registered. Please enter your first name:");
    }

    private void handlePendingUserRegistration(long chatId, String message, org.telegram.telegrambots.meta.api.objects.User from) {
        User user = pendingUsers.get(chatId);
        if (user.getFirstName() == null) {
            user.setFirstName(message);
            sendMessageAnswer(chatId, "Please enter your last name:");
        } else if (user.getLastName() == null) {
            user.setLastName(message);
            sendMessageAnswer(chatId, "Please enter your email:");
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
            String message1 = "Registration complete! You can now use the bot ";
            sendMessageWithCommands(chatId, message1);
        }
    }


    private void startHealthChecking(long chatId) {
        if (isUserRegistered(chatId)) {
            log.info("Yeha");
            sendMessageAnswer(chatId, "Please enter your Ciastolic and Diastolic pressure: (e.g - 120/80)");
            userStates.put(chatId, "AWAITING_PRESSURE");
        } else {
            sendMessageAnswer(chatId, "Ooops! You need to register first. Please use the /register command.");
        }
    }

    private boolean isUserRegistered(long chatId) {
        Optional<User> user = userRepository.findByChatId(chatId);
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
        String botUsername = getBotUsername();
        String answer = "Hi, " + firstName + "! My name is " + botUsername + ".\n\n" +
                "Here are the commands you can use:";
        sendMessageWithCommands(chatId, answer);
    }

    private void sendMessageWithCommands(long chatId, String resultMessage) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(resultMessage);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // First row
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Start Health Checking"));
        row.add(new KeyboardButton("My measurements"));
        keyboard.add(row);

        // Second row
        row = new KeyboardRow();
        row.add(new KeyboardButton("Check your BMI"));
        row.add(new KeyboardButton("Show Graphic"));
        row.add(new KeyboardButton("/audioBloodPressure"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);

        message.setReplyMarkup(keyboardMarkup);

        sendMessage(message);
    }

    public void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }
}
