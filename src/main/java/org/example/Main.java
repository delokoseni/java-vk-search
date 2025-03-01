package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.UserAuthResponse;
import com.vk.api.sdk.objects.friends.responses.GetResponse;
import com.vk.api.sdk.objects.users.Fields;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {

    private static int APP_ID; // Вставьте ваш APP_ID
    private static String CLIENT_SECRET; // Вставьте ваш CLIENT_SECRET
    private static String REDIRECT_URI; // Вставьте ваш Redirect URI

    public static void main(String[] args) {
        EnvLoader.load();
        APP_ID= Integer.parseInt(System.getProperty("APP_ID"));
        CLIENT_SECRET = System.getProperty("CLIENT_SECRET");
        REDIRECT_URI = System.getProperty("REDIRECT_URI");
        String code = System.getProperty("CODE");
        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        UserAuthResponse authResponse;

        try {
            // Получаем токен доступа
            authResponse = vk.oAuth()
                    .userAuthorizationCodeFlow(APP_ID, CLIENT_SECRET, REDIRECT_URI, code)
                    .execute();

            // Создание UserActor
            UserActor actor = new UserActor(Long.valueOf(authResponse.getUserId()), authResponse.getAccessToken());
            GroupActor groupActor = new GroupActor((long) APP_ID, CLIENT_SECRET);
            // Пытаемся выполнить запрос к API
            var info = vk.users().get(actor)
                    .userIds(String.valueOf(actor.getId())) // Получаем информацию о себе
                    .execute();

            System.out.println("Успешно подключились к API ВКонтакте!");
            System.out.println(info);

            GetResponse friendsList = vk.friends().get(actor).execute();
            System.out.println("Список ID ваших друзей:");
            for (var friendId : friendsList.getItems()) {
                System.out.println(friendId);
                TimeUnit.MILLISECONDS.sleep(250);
                var info2 = vk.users().get(actor)
                        .userIds(String.valueOf(friendId)) // Получаем информацию о себе
                        .execute();
                System.out.println(info2);
            }

            System.out.println("Успешно подключились к API ВКонтакте! Количество друзей: " + friendsList.getCount());

            // Третий запрос: получение ID пользователя с клавиатуры и запрос списка друзей
            Scanner scanner = new Scanner(System.in);
            System.out.print("Введите ID пользователя: ");
            long targetUserId = scanner.nextLong();

            GetResponse targetUserFriendsList = vk.friends().get(actor)
                    .userId(targetUserId) // Указываем ID целевого пользователя
                    .execute();

            System.out.println("Список ID друзей пользователя с ID " + targetUserId + ":");
            for (Long friendId : targetUserFriendsList.getItems()) {
                System.out.println(friendId);
            }

            System.out.println("Количество друзей у пользователя с ID " + targetUserId + ": " + targetUserFriendsList.getCount());

        } catch (ApiException | ClientException | InterruptedException e) {
            System.out.println("Ошибка подключения к API: " + e.getMessage());
        }
    }
}