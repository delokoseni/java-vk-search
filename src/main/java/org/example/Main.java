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

/**
 * Главный класс приложения для работы с VK API и сбора информации о друзьях пользователя.
 */
public class Main {

    private static final String USER_ID_URL = "https://vk.com/id"; // Формат URL для ID пользователя
    private static final String TXT_FILE_HEADER = "Link First Name Last Name"; // Заголовок для TXT
    private static final String CSV_HEADER = "Link,First Name,Last Name"; // Заголовок для CSV
    private static final String EXCEL_SHEET_NAME = "Друзья"; // Название листа в Excel

    private static int APP_ID;
    private static String CLIENT_SECRET;
    private static String REDIRECT_URI;
    private static UserActor actor;

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
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
            Long targetUserId = getUserIdFromUrl(vk, profileUrl);

            if (targetUserId != null) {
                GetResponse targetUserFriendsList = vk.friends().get(actor)
                        .userId(targetUserId)
                        .execute();
                System.out.println("Программа работает, подождите.");
                List<UserFull> friendsInfo = fetchFriendsInfo(vk, targetUserFriendsList);

                System.out.println("Выберите формат для сохранения информации:");
                System.out.println("1. TXT файл");
                System.out.println("2. CSV файл");
                System.out.println("3. Excel файл");
                int choice = scanner.nextInt();
                scanner.nextLine();

                // Название файла по короткому имени пользователя
                String fileName = profileUrl.substring(profileUrl.lastIndexOf("/") + 1);

                switch (choice) {
                    case 1:
                        saveToFile(fileName + ".txt", friendsInfo, FileType.TXT);
                        break;
                    case 2:
                        saveToFile(fileName + ".csv", friendsInfo, FileType.CSV);
                        break;
                    case 3:
                        saveToFile(fileName + ".xlsx", friendsInfo, FileType.EXCEL);
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

    /**
     * Получает ID пользователя из ссылки на его профиль.
     *
     * @param vk VkApiClient для выполнения запросов
     * @param profileUrl URL профиля пользователя
     * @return идентификатор пользователя или null, если он не найден
     */
    private static Long getUserIdFromUrl(VkApiClient vk, String profileUrl) {
        Pattern pattern = Pattern.compile("vk.com/([\\w.-]+)");
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

    /**
     * Получает ID пользователя по его короткому имени.
     *
     * @param vk VkApiClient для выполнения запросов
     * @param screenName короткое имя пользователя
     * @return идентификатор пользователя или null, если он не найден
     */
    private static Long fetchUserId(VkApiClient vk, String screenName) throws ApiException, ClientException {
        var response = vk.users().get(actor)
                .userIds(screenName)
                .execute();
        if (!response.isEmpty()) {
            return response.getFirst().getId();
        }
        return null;
    }

    /**
     * Получает информацию о друзьях пользователя.
     *
     * @param vk VkApiClient для выполнения запросов
     * @param targetUserFriendsList список друзей целевого пользователя
     * @return список объектов UserFull, содержащих информацию о друзьях
     */
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

    /**
     * Сохраняет данные о друзьях в указанный файл в зависимости от формата.
     *
     * @param fileName полное имя файла для сохранения
     * @param friendsInfo список объектов UserFull с информацией о друзьях
     * @param fileType тип файла для сохранения (TXT, CSV, Excel)
     */
    private static void saveToFile(String fileName, List<UserFull> friendsInfo, FileType fileType) {
        switch (fileType) {
            case TXT:
                saveToTxt(fileName, friendsInfo);
                break;
            case CSV:
                saveToCsv(fileName, friendsInfo);
                break;
            case EXCEL:
                saveToExcel(fileName, friendsInfo);
                break;
        }
    }

    /**
     * Сохраняет данные о друзьях в TXT файл.
     *
     * @param fileName полное имя файла для сохранения
     * @param friendsInfo список объектов UserFull с информацией о друзьях
     */
    private static void saveToTxt(String fileName, List<UserFull> friendsInfo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(TXT_FILE_HEADER);
            writer.newLine();
            for (UserFull friend : friendsInfo) {
                writer.write(USER_ID_URL + friend.getId() + " " + friend.getFirstName() + " " + friend.getLastName());
                writer.newLine();
            }
            System.out.println("Данные успешно сохранены в " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных в TXT файл: " + e.getMessage());
        }
    }

    /**
     * Сохраняет данные о друзьях в CSV файл.
     *
     * @param fileName полное имя файла для сохранения
     * @param friendsInfo список объектов UserFull с информацией о друзьях
     */
    private static void saveToCsv(String fileName, List<UserFull> friendsInfo) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (UserFull friend : friendsInfo) {
                writer.write(USER_ID_URL + friend.getId() + "," + friend.getFirstName() + "," + friend.getLastName());
                writer.newLine();
            }
            System.out.println("Данные успешно сохранены в " + fileName);
        } catch (IOException e) {
            System.out.println("Ошибка сохранения данных в CSV файл: " + e.getMessage());
        }
    }

    /**
     * Сохраняет данные о друзьях в Excel файл.
     *
     * @param fileName полное имя файла для сохранения
     * @param friendsInfo список объектов UserFull с информацией о друзьях
     */
    private static void saveToExcel(String fileName, List<UserFull> friendsInfo) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(EXCEL_SHEET_NAME);

        int rowNum = 0;
        Row header = sheet.createRow(rowNum++);
        header.createCell(0).setCellValue("Link");
        header.createCell(1).setCellValue("First Name");
        header.createCell(2).setCellValue("Last Name");

        for (UserFull friend : friendsInfo) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(USER_ID_URL + friend.getId());
            row.createCell(1).setCellValue(friend.getFirstName());
            row.createCell(2).setCellValue(friend.getLastName());
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

    /**
     * Перечисление для определения типов файлов.
     */
    private enum FileType {
        TXT,
        CSV,
        EXCEL
    }
}
