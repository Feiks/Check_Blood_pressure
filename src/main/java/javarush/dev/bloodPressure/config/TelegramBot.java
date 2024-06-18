package javarush.dev.bloodPressure.config;

import javarush.dev.bloodPressure.entity.User;
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

import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final String HELP_TEXT = "This bot is created to demonstrate capabilities \n\n " + "You can execute commands from the main menu \n\n" + "Type /start to see welcome message \n\n" + "Type /mydata to see data stored about yourself \n\n" + "Type /help to see message again \n\n";

    private Map<Long, User> pendingUsers = new HashMap<>();
    private final BotConfig botConfig;
    private final long defaultAdminChatId = 1003560212;
    private final UserRepository userRepository;
    private Map<Long, String> userTokens = new HashMap<>();


    public TelegramBot(BotConfig botConfig, UserRepository userRepository) {
        this.botConfig = botConfig;
        this.userRepository = userRepository;
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
            switch (message) {
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "Start Health Checking":
                    startHealthChecking(chatId);
                    break;
                case "/register":
                    startRegistration(chatId);
                    break;

            }
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
                sendMessageAnswer(chatId, "Please enter your IT field:");
            } else if (user.getItField() == null) {
                user.setItField(message);
                sendMessageAnswer(chatId, "Please enter your years of experience:");
            } else {
                try {
                    int yearsOfExperience = Integer.parseInt(message);
                    user.setYearsOfExperience(yearsOfExperience);
                    user.setUsername(from.getUserName());
                    userRepository.save(user);
                    pendingUsers.remove(chatId);
                    sendMessageAnswer(chatId, "Registration complete! You can now use the bot.");
                } catch (NumberFormatException e) {
                    sendMessageAnswer(chatId, "Please enter a valid number for years of experience:");
                }
            }
        }
    }

    private void startHealthChecking(long chatId) {
        if (isUserRegistered(chatId)) {
            log.info("Yeha");
        } else {
            sendMessageAnswer(chatId, "You need to register first. Please use the /register command.");
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

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();
        row1.add("weather");
        row1.add("get random joke");
        KeyboardRow row2 = new KeyboardRow();
        row2.add("register");
        row2.add("check my data");
        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
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

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(answer);

        // Create the keyboard markup
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // First row
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("/help"));
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
