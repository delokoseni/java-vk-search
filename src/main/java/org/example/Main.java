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

import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

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
                for (var friendId : targetUserFriendsList.getItems()) {
                    System.out.println(friendId);
                    TimeUnit.MILLISECONDS.sleep(250);
                    var info2 = vk.users().get(actor)
                            .userIds(String.valueOf(friendId)) // Получаем информацию о себе
                            .execute();
                    System.out.println(info2);
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
                return fetchUserId(vk, screenName); // Передаем vk как параметр
            } catch (ApiException | ClientException e) {
                System.out.println("Ошибка получения ID пользователя: " + e.getMessage());
            }
        }
        return null;
    }

    private static Long fetchUserId(VkApiClient vk, String screenName) throws ApiException, ClientException {
        // Получаем информацию о пользователе по его screen_name
        var response = vk.users().get(actor)
                .userIds(screenName)
                .execute();

        if (!response.isEmpty()) {
            return response.get(0).getId(); // Возвращаем ID пользователя
        }
        return null; // Если не нашли пользователя
    }
}
