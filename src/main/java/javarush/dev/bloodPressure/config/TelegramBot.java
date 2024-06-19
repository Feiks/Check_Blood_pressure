package javarush.dev.bloodPressure.config;

import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import javarush.dev.bloodPressure.entity.User;
import javarush.dev.bloodPressure.repo.BloodMeasurementRepository;
import javarush.dev.bloodPressure.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
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
            } else {
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
                        case "/register":
                        case "register":
                            startRegistration(chatId);
                            break;
                        case "Check your BMI":
                            checkMyIBF(chatId);
                            break;
                        default:
                            handleDefaultMessage(chatId, message);
                            break;
                    }
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

                if (systolic < 90 || diastolic < 60) {
                    resultMessage += "Your blood pressure is low.";
                } else if (systolic <= 129 && diastolic <= 89) {
                    resultMessage += "Your blood pressure is normal.";
                } else {
                    resultMessage += "Your blood pressure is high. You should take your pills " + userOptional.get().getMedication();
                }

                sendMessageWithCommands(chatId, resultMessage);

            } catch (NumberFormatException e) {
                sendMessageAnswer(chatId, "Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
            }
        } else {
            sendMessageAnswer(chatId, "Invalid format. Please enter the values in the format: Systolic Diastolic (e.g., 120/80).");
        }
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
        } else {
            try {
                int age = Integer.parseInt(message);
                user.setAge(age);
                user.setUsername(from.getUserName());
                userRepository.save(user);
                pendingUsers.remove(chatId);
                SendMessage message1 = new SendMessage();
                message1.setChatId(String.valueOf(chatId));
                message1.setText("Registration complete! You can now use the bot.");
                sendMessage(message1);
            } catch (NumberFormatException e) {
                sendMessageAnswer(chatId, "Please enter a valid number for years of experience:");
            }
        }
    }


    private void startHealthChecking(long chatId) {
        if (isUserRegistered(chatId)) {
            log.info("Yeha");
            sendMessageAnswer(chatId, "Please enter your Ciastolic and Diastolic pressure:");
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
        row.add(new KeyboardButton("Check your BMI"));
        keyboard.add(row);

        // Second row
        row = new KeyboardRow();
        row.add(new KeyboardButton("Start Health Checking"));
        row.add(new KeyboardButton("/register"));
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);

        message.setReplyMarkup(keyboardMarkup);

        sendMessage(message);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }
}
