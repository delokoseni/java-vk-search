package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.UserAuthResponse;
import com.vk.api.sdk.objects.friends.responses.GetResponse;

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

            // Пытаемся выполнить запрос к API
            var info = vk.users().get(actor)
                    .userIds(String.valueOf(actor.getId())) // Получаем информацию о себе
                    .execute();

            System.out.println("Успешно подключились к API ВКонтакте!");
            System.out.println(info);
            GetResponse friendsList = vk.friends().get(actor).execute();
            System.out.println("Список ID ваших друзей:");
            for (Long friendId : friendsList.getItems()) {
                System.out.println(friendId);
            }

            System.out.println("Успешно подключились к API ВКонтакте! Количество друзей: " + friendsList.getCount());
        } catch (ApiException | ClientException e) {
            System.out.println("Ошибка подключения к API: " + e.getMessage());
        }
    }
}