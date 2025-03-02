package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.UserAuthResponse;
import com.vk.api.sdk.objects.friends.responses.GetResponse;
import com.vk.api.sdk.objects.users.UserFull;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static int APP_ID; // Вставьте ваш APP_ID
    private static String CLIENT_SECRET; // Вставьте ваш CLIENT_SECRET
    private static String REDIRECT_URI; // Вставьте ваш Redirect URI
    private static UserActor actor; // Добавляем actor на уровне класса

    public static void main(String[] args) {
        EnvLoader.load();
        APP_ID = Integer.parseInt(System.getProperty("APP_ID"));
        CLIENT_SECRET = System.getProperty("CLIENT_SECRET");
        REDIRECT_URI = System.getProperty("REDIRECT_URI");
        String code = System.getProperty("CODE");

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        UserAuthResponse authResponse;

        try {
            authResponse = vk.oAuth()
                    .userAuthorizationCodeFlow(APP_ID, CLIENT_SECRET, REDIRECT_URI, code)
                    .execute();
            actor = new UserActor(Long.valueOf(authResponse.getUserId()), authResponse.getAccessToken());
            Scanner scanner = new Scanner(System.in);
            System.out.print("Введите ссылку на профиль пользователя VK: ");
            String profileUrl = scanner.nextLine();
            Long targetUserId = getUserIdFromUrl(vk, profileUrl); // Передаем vk как параметр

            if (targetUserId != null) {
                GetResponse targetUserFriendsList = vk.friends().get(actor)
                        .userId(targetUserId) // Указываем ID целевого пользователя
                        .execute();
                List<UserFull> friendsInfo = fetchFriendsInfo(vk, targetUserFriendsList);

                // Запрос формата сохранения
                System.out.println("Выберите формат для сохранения информации:");
                System.out.println("1. TXT файл");
                System.out.println("2. CSV файл");
                System.out.println("3. Excel файл");
                int choice = scanner.nextInt();
                scanner.nextLine(); // consume the newline

                System.out.print("Введите имя файла для сохранения (без расширения): ");
                String fileName = scanner.nextLine();

                switch (choice) {
                    case 1:
                        saveToTxt(fileName + ".txt", friendsInfo);
                        break;
                    case 2:
                        saveToCsv(fileName + ".csv", friendsInfo);
                        break;
                    case 3:
                        saveToExcel(fileName + ".xlsx", friendsInfo);
                        break;
                    default:
                        System.out.println("Некорректный выбор формата.");
                }
            } else {
                System.out.println("Не удалось получить ID пользователя из ссылки.");
            }
        } catch (ApiException | ClientException | InterruptedException e) {
            System.out.println("Ошибка подключения к API: " + e.getMessage());
        }
    }

    private static Long getUserIdFromUrl(VkApiClient vk, String profileUrl) {
        Pattern pattern = Pattern.compile("vk.com/(\\w+)");
        Matcher matcher = pattern.matcher(profileUrl);

        if (matcher.find()) {
            String screenName = matcher.group(1);
            try {
                return fetchUserId(vk, screenName);
            } catch (ApiException | ClientException e) {
                System.out.println("Ошибка получения ID пользователя: " + e.getMessage());
            }
        }
        return null;
    }

    private static Long fetchUserId(VkApiClient vk, String screenName) throws ApiException, ClientException {
        var response = vk.users().get(actor)
                .userIds(screenName)
                .execute();
        if (!response.isEmpty()) {
            return response.get(0).getId();
        }
        return null;
    }

    private static List<UserFull> fetchFriendsInfo(VkApiClient vk, GetResponse targetUserFriendsList) throws ApiException, ClientException, InterruptedException {
        List<UserFull> friendsInfo = new ArrayList<>();
        for (var friendId : targetUserFriendsList.getItems()) {
            TimeUnit.MILLISECONDS.sleep(250);
            var info2 = vk.users().get(actor)
                    .userIds(String.valueOf(friendId))
                    .execute();
            friendsInfo.addAll(info2);
        }
        return friendsInfo;
    }

    private static void saveToTxt(String fileName, List<UserFull> friendsInfo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (UserFull friend : friendsInfo) {
                writer.write(friend.getFirstName() + " " + friend.getLastName());
                writer.newLine();
            }
            System.out.println("Данные успешно сохранены в " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных в TXT файл: " + e.getMessage());
        }
    }

    private static void saveToCsv(String fileName, List<UserFull> friendsInfo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("First Name,Last Name");
            writer.newLine();
            for (UserFull friend : friendsInfo) {
                writer.write(friend.getFirstName() + "," + friend.getLastName());
                writer.newLine();
            }
            System.out.println("Данные успешно сохранены в " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных в CSV файл: " + e.getMessage());
        }
    }

    private static void saveToExcel(String fileName, List<UserFull> friendsInfo) {
        // Для работы с Excel-файлами вам потребуется библиотека Apache POI.
        // Убедитесь, что вы добавили соответствующую зависимость в ваш проект.

        Workbook workbook = new XSSFWorkbook(); // Проверить, подключена ли библиотека

        Sheet sheet = workbook.createSheet("Друзья");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("First Name");
        header.createCell(1).setCellValue("Last Name");

        int rowNum = 1;
        for (UserFull friend : friendsInfo) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(friend.getFirstName());
            row.createCell(1).setCellValue(friend.getLastName());
        }

        try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
            workbook.write(outputStream);
            System.out.println("Данные успешно сохранены в " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных в Excel файл: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                System.out.println("Ошибка закрытия Excel workbook: " + e.getMessage());
            }
        }
    }
}
