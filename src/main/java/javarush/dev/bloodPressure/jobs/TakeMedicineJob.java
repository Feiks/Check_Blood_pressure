package javarush.dev.bloodPressure.jobs;

import javarush.dev.bloodPressure.config.TelegramBot;
import javarush.dev.bloodPressure.entity.User;
import javarush.dev.bloodPressure.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableScheduling
public class TakeMedicineJob {
    private final UserRepository userRepository;

    private  final TelegramBot telegramBotService;

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void checkAndSendReminders() {
        List<User> users = userRepository.findAll();
        LocalTime currentTime = LocalTime.now();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

        for (User user : users) {
            try {
                Date medicineTime = dateFormat.parse(user.getMedicineTime());
                LocalTime reminderTime = medicineTime.toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalTime().minusMinutes(5);

                if (currentTime.isAfter(reminderTime) && currentTime.isBefore(reminderTime.plusMinutes(5))) {
                    sendReminder(user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendReminder(User user) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(user.getChatId()));
        message.setText("Time to take your blood pressure medicine: " + user.getMedication());
        telegramBotService.sendMessage(message);
    }

}
