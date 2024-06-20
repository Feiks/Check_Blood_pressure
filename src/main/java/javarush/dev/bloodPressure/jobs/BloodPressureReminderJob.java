package javarush.dev.bloodPressure.jobs;

import javarush.dev.bloodPressure.config.TelegramBot;
import javarush.dev.bloodPressure.entity.BloodPressureMeasurement;
import javarush.dev.bloodPressure.entity.User;
import javarush.dev.bloodPressure.repo.BloodMeasurementRepository;
import javarush.dev.bloodPressure.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableScheduling
public class BloodPressureReminderJob {
    private final UserRepository userRepository;
    private final TelegramBot telegramBotService;
    private final BloodMeasurementRepository measurementRepository;

    @Scheduled(cron = "0 30 8 * * ?")
    public void sendBloodPressureReminder() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            telegramBotService.sendMessageWithCommands(user.getChatId(), "Good morning! It's 8:30 AM. Time to measure your morning blood pressure.");
        }
    }

    @Scheduled(cron = "0 0 21 * * ?") // Run at 9:00 PM every day
    public void sendEveningReminder() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            telegramBotService.sendMessageWithCommands(user.getChatId(), "Good evening! It's 9:00 PM. Time to measure your evening blood pressure.");
        }
    }

    @Scheduled(cron = "0 0 22 * * ?") // Run at 10:00 PM every day
    public void sendDailyStats() {
        List<User> users = userRepository.findAll();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        for (User user : users) {
            List<BloodPressureMeasurement> measurements = measurementRepository.findByUserAndMeasurementTimeBetween(user, startOfDay, endOfDay);

            if (!measurements.isEmpty()) {
                double avgSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).average().orElse(0);
                double avgDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).average().orElse(0);
                int maxSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).max().orElse(0);
                int minSystolic = measurements.stream().mapToInt(BloodPressureMeasurement::getSystolic).min().orElse(0);
                int maxDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).max().orElse(0);
                int minDiastolic = measurements.stream().mapToInt(BloodPressureMeasurement::getDiastolic).min().orElse(0);

                String messageText = String.format(
                        "Here are your daily blood pressure stats:\n" +
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
                if ((maxDiastolic - minDiastolic) > 11 || (maxSystolic - minSystolic) > 11) {
                    sendReminderMessage(user, "Your blood pressure is increased , please contact your doctor");
                }
                telegramBotService.sendMessageWithCommands(user.getChatId(), messageText);
            } else {
                telegramBotService.sendMessageWithCommands(user.getChatId(), "No blood pressure measurements recorded today.");
            }
        }
    }

    private void sendReminderMessage(User user, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(user.getChatId()));
        message.setText(messageText);
        telegramBotService.sendMessage(message);
    }
}