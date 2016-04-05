/*
 * This file is part of SPFBL.
 *
 * SPFBL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPFBL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPFBL.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.spfbl.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import net.spfbl.core.Server;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeSet;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import net.spfbl.data.Block;
import net.spfbl.core.Client;
import net.spfbl.core.Core;
import net.spfbl.core.Defer;
import net.spfbl.core.ProcessException;
import net.spfbl.data.NoReply;
import net.spfbl.data.Provider;
import net.spfbl.data.White;
import net.spfbl.spf.SPF;
import net.spfbl.whois.Domain;
import net.spfbl.whois.Subnet;
import net.tanesha.recaptcha.ReCaptcha;
import net.tanesha.recaptcha.ReCaptchaFactory;
import net.tanesha.recaptcha.ReCaptchaResponse;
import org.apache.commons.lang3.SerializationUtils;

/**
 * Servidor de consulta em SPF.
 *
 * Este serviço responde a consulta e finaliza a conexão logo em seguida.
 *
 * @author Leandro Carlos Rodrigues <leandro@spfbl.net>
 */
public final class ServerHTTP extends Server {

    private final String HOSTNAME;
    private final int PORT;
    private final HttpServer SERVER;

    private final HashMap<String,String> MAP = new HashMap<String,String>();

    public synchronized HashMap<String,String> getMap() {
        HashMap<String,String> map = new HashMap<String,String>();
        map.putAll(MAP);
        return map;
    }

    public synchronized String drop(String domain) {
        return MAP.remove(domain);
    }

    public synchronized boolean put(String domain, String url) {
        try {
            domain = Domain.normalizeHostname(domain, false);
            if (domain == null) {
                return false;
            } else if (url == null || url.equals("NONE")) {
                MAP.put(domain, null);
                return true;
            } else {
                new URL(url);
                if (url.endsWith("/spam/")) {
                    MAP.put(domain, url);
                    return true;
                } else {
                    return false;
                }
            }
        } catch (MalformedURLException ex) {
            return false;
        }

    }

    public synchronized void store() {
        try {
            long time = System.currentTimeMillis();
            File file = new File("./data/url.map");
            if (MAP.isEmpty()) {
                file.delete();
            } else {
                FileOutputStream outputStream = new FileOutputStream(file);
                try {
                    SerializationUtils.serialize(MAP, outputStream);
                } finally {
                    outputStream.close();
                }
                Server.logStore(time, file);
            }
        } catch (Exception ex) {
            Server.logError(ex);
        }
    }

    public synchronized void load() {
        long time = System.currentTimeMillis();
        File file = new File("./data/url.map");
        if (file.exists()) {
            try {
                HashMap<String,String> map;
                FileInputStream fileInputStream = new FileInputStream(file);
                try {
                    map = SerializationUtils.deserialize(fileInputStream);
                } finally {
                    fileInputStream.close();
                }
                MAP.putAll(map);
                Server.logLoad(time, file);
            } catch (Exception ex) {
                Server.logError(ex);
            }
        }
    }

    /**
     * Configuração e intanciamento do servidor.
     * @param port a porta HTTPS a ser vinculada.
     * @throws java.io.IOException se houver falha durante o bind.
     */
    public ServerHTTP(String hostname, int port) throws IOException {
        super("SERVERHTP");
        HOSTNAME = hostname;
        PORT = port;
        setPriority(Thread.NORM_PRIORITY);
        // Criando conexões.
        Server.logDebug("binding HTTP socket on port " + port + "...");
        SERVER = HttpServer.create(new InetSocketAddress(port), 0);
        SERVER.createContext("/", new ComplainHandler());
        SERVER.setExecutor(null); // creates a default executor
    }

    public String getSpamURL() {
        if (HOSTNAME == null) {
            return null;
        } else {
            return "http://" + HOSTNAME + (PORT == 80 ? "" : ":" + PORT) + "/spam/";
        }
    }

    public String getReleaseURL() {
        if (HOSTNAME == null) {
            return null;
        } else {
            return "http://" + HOSTNAME + (PORT == 80 ? "" : ":" + PORT) + "/release/";
        }
    }
    
    public String getUnblockURL() {
        if (HOSTNAME == null) {
            return null;
        } else {
            return "http://" + HOSTNAME + (PORT == 80 ? "" : ":" + PORT) + "/unblock/";
        }
    }
    
    public String getWhiteURL() {
        if (HOSTNAME == null) {
            return null;
        } else {
            return "http://" + HOSTNAME + (PORT == 80 ? "" : ":" + PORT) + "/white/";
        }
    }

    public synchronized String getSpamURL(String domain) {
        if (MAP.containsKey(domain)) {
            return MAP.get(domain);
        } else if (HOSTNAME == null) {
            return null;
        } else {
            return "http://" + HOSTNAME + (PORT == 80 ? "" : ":" + PORT) + "/spam/";
        }
    }

    private static String getOrigin(HttpExchange exchange) {
        InetSocketAddress socketAddress = exchange.getRemoteAddress();
        InetAddress address = socketAddress.getAddress();
        Client client = Client.get(address);
        if (client == null) {
            return address.getHostAddress();
        } else {
            return address.getHostAddress() + ' ' + client.getDomain();
        }
    }

    private static String getRemoteAddress(HttpExchange exchange) {
        InetSocketAddress socketAddress = exchange.getRemoteAddress();
        InetAddress address = socketAddress.getAddress();
        return address.getHostAddress();
    }

    @SuppressWarnings("unchecked")
    private static HashMap<String,Object> getParameterMap(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "UTF-8");
        BufferedReader br = new BufferedReader(isr);
        String query = br.readLine();
        return getParameterMap(query);
    }

    @SuppressWarnings("unchecked")
    private static HashMap<String,Object> getParameterMap(String query) throws UnsupportedEncodingException {
        if (query == null) {
            return null;
        } else {
            TreeSet<String> identifierSet = new TreeSet<String>();
            HashMap<String,Object> map = new HashMap<String,Object>();
            String pairs[] = query.split("[&]");
            for (String pair : pairs) {
                String param[] = pair.split("[=]");
                String key = null;
                String value = null;
                if (param.length > 0) {
                    key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
                }
                if (param.length > 1) {
                    value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));
                }
                if ("identifier".equals(key)) {
                    identifierSet.add(value);
                } else {
                    map.put(key, value);
                }
            }
            if (!identifierSet.isEmpty()) {
                map.put("identifier", identifierSet);
            }
            return map;
        }
    }

    private static class ComplainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                long time = System.currentTimeMillis();
                Thread.currentThread().setName("HTTPCMMND");
                String request = exchange.getRequestMethod();
                URI uri = exchange.getRequestURI();
                String command = uri.toString();
                String origin = getOrigin(exchange);
                int code;
                String result;
                String type;
                if (request.equals("POST")) {
                    if (command.startsWith("/spam/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String recipient = SPF.getRecipient(ticket);
                            String client = SPF.getClient(ticket);
                            client = client == null ? "" : client + ':';
                            HashMap<String,Object> parameterMap = getParameterMap(exchange);
                            if (parameterMap.containsKey("identifier")) {
                                boolean valid = true;
                                TreeSet<String> identifierSet = (TreeSet<String>) parameterMap.get("identifier");
                                if (Core.hasRecaptchaKeys()) {
                                    if (parameterMap.containsKey("recaptcha_challenge_field")
                                            && parameterMap.containsKey("recaptcha_response_field")
                                            ) {
                                        // reCAPCHA convencional.
                                        String recaptchaPublicKey = Core.getRecaptchaKeySite();
                                        String recaptchaPrivateKey = Core.getRecaptchaKeySecret();
                                        String remoteAddress = getRemoteAddress(exchange);
                                        ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaPublicKey, recaptchaPrivateKey, true);
                                        String recaptchaChallenge = (String) parameterMap.get("recaptcha_challenge_field");
                                        String recaptchaResponse = (String) parameterMap.get("recaptcha_response_field");
                                        ReCaptchaResponse response = captcha.checkAnswer(remoteAddress, recaptchaChallenge, recaptchaResponse);
                                        valid = response.isValid();
                                    } else if (parameterMap.containsKey("g-recaptcha-response")) {
                                        // TODO: novo reCAPCHA.
                                        valid = false;
                                    } else {
                                        // reCAPCHA necessário.
                                        valid = false;
                                    }
                                }
                                TreeSet<String> tokenSet = SPF.getTokenSet(ticket);
                                if (valid) {
                                    TreeSet<String> blockSet = new TreeSet<String>();
                                    for (String identifier : identifierSet) {
                                        if (tokenSet.contains(identifier)) {
                                            long time2 = System.currentTimeMillis();
                                            String block = client + identifier + '>' + recipient;
                                            if (Block.addExact(block)) {
                                                Server.logQuery(
                                                        time2, "BLOCK",
                                                        origin,
                                                        "BLOCK ADD " + block,
                                                        "ADDED"
                                                        );
                                            }
                                            blockSet.add(identifier);
                                        }
                                    }
                                    type = "HTTPC";
                                    code = 200;
                                    result = "Bloqueados: " + blockSet + " >" + recipient + "\n";
                                } else {
                                    type = "HTTPC";
                                    code = 200;
                                    String message = "O desafio do reCAPTCHA não foi resolvido.";
                                    result = getComplainHMTL(tokenSet, identifierSet, message, true);
                                }
                            } else {
                                type = "HTTPC";
                                code = 500;
                                result = "Identificadores indefinidos.\n";
                            }
                        } catch (Exception ex) {
                            type = "HTTPC";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/release/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            index = registry.indexOf(' ');
                            Date date = Server.parseTicketDate(registry.substring(0, index));
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "DEFER";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de liberação do SPFBL",
                                        "Este ticket de liberação está vencido."
                                        );
                            } else {
                                boolean valid = true;
                                if (Core.hasRecaptchaKeys()) {
                                    HashMap<String,Object> parameterMap = getParameterMap(exchange);
                                    if (parameterMap.containsKey("recaptcha_challenge_field")
                                            && parameterMap.containsKey("recaptcha_response_field")
                                            ) {
                                        // reCAPCHA convencional.
                                        String recaptchaPublicKey = Core.getRecaptchaKeySite();
                                        String recaptchaPrivateKey = Core.getRecaptchaKeySecret();
                                        String remoteAddress = getRemoteAddress(exchange);
                                        ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaPublicKey, recaptchaPrivateKey, true);
                                        String recaptchaChallenge = (String) parameterMap.get("recaptcha_challenge_field");
                                        String recaptchaResponse = (String) parameterMap.get("recaptcha_response_field");
                                        ReCaptchaResponse response = captcha.checkAnswer(remoteAddress, recaptchaChallenge, recaptchaResponse);
                                        valid = response.isValid();
                                    } else if (parameterMap.containsKey("g-recaptcha-response")) {
                                        // TODO: novo reCAPCHA.
                                        valid = false;
                                    } else {
                                        // reCAPCHA necessário.
                                        valid = false;
                                    }
                                }
                                if (valid) {
                                    String id = registry.substring(index + 1);
                                    String title = "Página de liberação do SPFBL";
                                    String message;
                                    if (Defer.release(id)) {
                                        message = "Sua mensagem foi liberada com sucesso.";
                                    } else {
                                        message = "Sua mensagem já havia sido liberada.";
                                    }
                                    type = "DEFER";
                                    code = 200;
                                    result = getMessageHMTL(title, message);
                                } else {
                                    type = "DEFER";
                                    code = 200;
                                    result = getReleaseHMTL(
                                            "O desafio reCAPTCHA não foi resolvido. "
                                            + "Tente novamente."
                                            );
                                }
                            }
                        } catch (Exception ex) {
                            type = "SPFSP";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/unblock/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            StringTokenizer tokenizer = new StringTokenizer(registry, " ");
                            Date date = Server.parseTicketDate(tokenizer.nextToken());
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "BLOCK";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Este ticket de desbloqueio está vencido."
                                        );
                            } else {
                                boolean valid = true;
                                if (Core.hasRecaptchaKeys()) {
                                    HashMap<String,Object> parameterMap = getParameterMap(exchange);
                                    if (parameterMap.containsKey("recaptcha_challenge_field")
                                            && parameterMap.containsKey("recaptcha_response_field")
                                            ) {
                                        // reCAPCHA convencional.
                                        String recaptchaPublicKey = Core.getRecaptchaKeySite();
                                        String recaptchaPrivateKey = Core.getRecaptchaKeySecret();
                                        String remoteAddress = getRemoteAddress(exchange);
                                        ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaPublicKey, recaptchaPrivateKey, true);
                                        String recaptchaChallenge = (String) parameterMap.get("recaptcha_challenge_field");
                                        String recaptchaResponse = (String) parameterMap.get("recaptcha_response_field");
                                        ReCaptchaResponse response = captcha.checkAnswer(remoteAddress, recaptchaChallenge, recaptchaResponse);
                                        valid = response.isValid();
                                    } else if (parameterMap.containsKey("g-recaptcha-response")) {
                                        // TODO: novo reCAPCHA.
                                        valid = false;
                                    } else {
                                        // reCAPCHA necessário.
                                        valid = false;
                                    }
                                }
                                if (valid) {
                                    String client = tokenizer.nextToken();
                                    String ip = tokenizer.nextToken();
                                    String sender = tokenizer.nextToken();
                                    String recipient = tokenizer.nextToken();
                                    String hostname = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
                                    client = client == null ? "" : client + ':';
                                    String mx = Domain.extractHost(sender, true);
                                    String origem = Provider.containsExact(mx) ? sender : mx;
                                    String white = client + origem + ";PASS>" + recipient;
                                    String url = Core.getWhiteURL(white, client, ip, sender, hostname, recipient);
                                    String title = "Página de desbloqueio do SPFBL";
                                    String message;
                                    if (enviarDesbloqueio(url, sender, recipient)) {
                                        Block.addExact(white);
                                        message = "A solicitação de desbloqueio foi "
                                                + "enviada para o destinatário '" + recipient + "'.\n"
                                                + "Aguarde pelo desbloqueio sem enviar novas mensagens.";
                                    } else {
                                        message = "Não foi possível enviar a solicitação de "
                                                + "desbloqueio para o destinatário "
                                                + "'" + recipient + "' devido a problemas técnicos.";
                                    }
                                    type = "BLOCK";
                                    code = 200;
                                    result = getMessageHMTL(title, message);
                                } else {
                                    type = "BLOCK";
                                    code = 200;
                                    result = getUnblockHMTL(
                                            "O desafio reCAPTCHA não foi resolvido. "
                                            + "Tente novamente."
                                            );
                                }
                            }
                        } catch (Exception ex) {
                            type = "SPFSP";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/white/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            StringTokenizer tokenizer = new StringTokenizer(registry, " ");
                            Date date = Server.parseTicketDate(tokenizer.nextToken());
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "WHITE";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Este ticket de desbloqueio está vencido."
                                        );
                            } else {
                                boolean valid = true;
                                if (Core.hasRecaptchaKeys()) {
                                    HashMap<String,Object> parameterMap = getParameterMap(exchange);
                                    if (parameterMap.containsKey("recaptcha_challenge_field")
                                            && parameterMap.containsKey("recaptcha_response_field")
                                            ) {
                                        // reCAPCHA convencional.
                                        String recaptchaPublicKey = Core.getRecaptchaKeySite();
                                        String recaptchaPrivateKey = Core.getRecaptchaKeySecret();
                                        String remoteAddress = getRemoteAddress(exchange);
                                        ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaPublicKey, recaptchaPrivateKey, true);
                                        String recaptchaChallenge = (String) parameterMap.get("recaptcha_challenge_field");
                                        String recaptchaResponse = (String) parameterMap.get("recaptcha_response_field");
                                        ReCaptchaResponse response = captcha.checkAnswer(remoteAddress, recaptchaChallenge, recaptchaResponse);
                                        valid = response.isValid();
                                    } else if (parameterMap.containsKey("g-recaptcha-response")) {
                                        // TODO: novo reCAPCHA.
                                        valid = false;
                                    } else {
                                        // reCAPCHA necessário.
                                        valid = false;
                                    }
                                }
                                if (valid) {
                                    String white = tokenizer.nextToken();
                                    String client = tokenizer.nextToken();
                                    String ip = tokenizer.nextToken();
                                    String sender = tokenizer.nextToken();
                                    String recipient = tokenizer.nextToken();
                                    String hostname = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
                                    client = client == null ? "" : client + ':';
                                    String title = "Página de desbloqueio do SPFBL";
                                    String message;
                                    if (White.addExact(white)) {
                                        Block.clear(client, ip, sender, hostname, "PASS", recipient);
                                        message = "O desbloqueio do remetente '" + sender + "' foi efetuado com sucesso.";
                                    } else {
                                        message = "O desbloqueio do remetente '" + sender + "' já havia sido evetudao.";
                                    }
                                    type = "WHITE";
                                    code = 200;
                                    result = getMessageHMTL(title, message);
                                } else {
                                    type = "WHITE";
                                    code = 200;
                                    result = getWhiteHMTL(
                                            "O desafio reCAPTCHA não foi resolvido. "
                                            + "Tente novamente."
                                            );
                                }
                            }
                        } catch (Exception ex) {
                            type = "SPFSP";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else {
                        type = "HTTPC";
                        code = 403;
                        result = "Forbidden\n";
                    }
                } else if (request.equals("GET")) {
                    if (command.startsWith("/spam/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String recipient = SPF.getRecipient(ticket);
                            boolean whiteBlockForm = recipient != null;
                            TreeSet<String> complainSet = SPF.addComplain(origin, ticket);
                            TreeSet<String> tokenSet = SPF.getTokenSet(ticket);
                            TreeSet<String> selectionSet = new TreeSet<String>();
                            String message;
                            if (complainSet == null) {
                                complainSet = SPF.getComplain(ticket);
                                message = "A mensagem já havia sido denunciada antes.";
                            } else {
                                message = "A mensagem foi denunciada com sucesso.";
                            }
                            for (String token : complainSet) {
                                if (!Subnet.isValidIP(token)) {
                                    selectionSet.add(token);
                                }
                            }
                            type = "SPFSP";
                            code = 200;
                            result = getComplainHMTL(tokenSet, selectionSet, message, whiteBlockForm);
                        } catch (Exception ex) {
                            type = "SPFSP";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/ham/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            TreeSet<String> tokenSet = SPF.deleteComplain(origin, ticket);
                            if (tokenSet == null) {
                                type = "SPFHM";
                                code = 404;
                                result = "ALREADY REMOVED\n";
                            } else {
                                type = "SPFHM";
                                code = 200;
                                String recipient = SPF.getRecipient(ticket);
                                result = "OK " + tokenSet + (recipient == null ? "" : " >" + recipient) + "\n";
                            }
                        } catch (Exception ex) {
                            type = "SPFHM";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/release/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            index = registry.indexOf(' ');
                            Date date = Server.parseTicketDate(registry.substring(0, index));
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "DEFER";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de liberação do SPFBL",
                                        "Este ticket de liberação está vencido."
                                        );
                            } else {
                                String id = registry.substring(index + 1);
                                Defer defer = Defer.getDefer(date, id);
                                if (defer == null) {
                                    type = "DEFER";
                                    code = 500;
                                    result = getMessageHMTL(
                                            "Página de liberação do SPFBL",
                                            "Este ticket de liberação não existe ou já foi liberado antes."
                                            );
                                } else if (defer.isReleased()) {
                                    type = "DEFER";
                                    code = 200;
                                    result = getMessageHMTL(
                                            "Página de liberação do SPFBL",
                                            "Sua mensagem já havia sido liberada."
                                            );
                                } else {
                                    type = "DEFER";
                                    code = 200;
                                    result = getReleaseHMTL(
                                            "Para liberar o recebimento da mensagem, "
                                            + "resolva o desafio reCAPTCHA abaixo."
                                            );
                                }
                            }
                        } catch (Exception ex) {
                            type = "DEFER";
                            code = 500;
                            result = getMessageHMTL(
                                    "Página de liberação do SPFBL",
                                    "Ocorreu um erro no processamento desta solicitação: "
                                    + ex.getMessage() == null ? "undefined error." : ex.getMessage()
                                    );
                        }
                    } else if (command.startsWith("/unblock/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            StringTokenizer tokenizer = new StringTokenizer(registry, " ");
                            Date date = Server.parseTicketDate(tokenizer.nextToken());
                            String client = tokenizer.nextToken();
                            String ip = tokenizer.nextToken();
                            String sender = tokenizer.nextToken();
                            String recipient = tokenizer.nextToken();
                            String hostname = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
                            client = client == null ? "" : client + ':';
                            String mx = Domain.extractHost(sender, true);
                            String origem = Provider.containsExact(mx) ? sender : mx;
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "BLOCK";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Este ticket de desbloqueio está vencido."
                                        );
                            } else if (sender == null || recipient == null) {
                                type = "BLOCK";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Este ticket de desbloqueio não "
                                        + "contém remetente e destinatário."
                                        );
                            } else if (White.containsExact(client + origem + ";PASS>" + recipient)) {
                                type = "BLOCK";
                                code = 200;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "O destinatário '" + recipient + "' "
                                        + "já autorizou o recebimento de mensagens "
                                        + "do remetente '" + sender + "'."
                                        );
                            } else if (Block.containsExact(client + origem + ";PASS>" + recipient)) {
                                type = "BLOCK";
                                code = 200;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "O destinatário '" + recipient + "' "
                                        + "não decidiu se quer receber mensagens "
                                        + "do remetente '" + sender + "'.\n"
                                        + "Para que a reputação deste remetente "
                                        + "não seja prejudicada neste sistema, "
                                        + "é necessário que ele pare de tentar "
                                        + "enviar mensagens para este "
                                        + "destinatário até a sua decisão.\n"
                                        + "Cada tentativa de envio por ele, "
                                        + "conta um ponto negativo na "
                                        + "reputação dele deste sistema."
                                        );
                            } else {
                                type = "BLOCK";
                                code = 200;
                                result = getUnblockHMTL(
                                            "Para solicitar o desbloqueio do remetente '" + sender + "'\n"
                                            + "diretamente para o destinatário '" + recipient + "',\n"
                                            + "resolva o desafio reCAPTCHA abaixo."
                                            );
                            }
                        } catch (Exception ex) {
                            type = "BLOCK";
                            code = 500;
                            result = getMessageHMTL(
                                    "Página de desbloqueio do SPFBL",
                                    "Ocorreu um erro no processamento desta solicitação: "
                                    + ex.getMessage() == null ? "undefined error." : ex.getMessage()
                                    );
                        }
                    } else if (command.startsWith("/white/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            String registry = Server.decrypt(ticket);
                            StringTokenizer tokenizer = new StringTokenizer(registry, " ");
                            Date date = Server.parseTicketDate(tokenizer.nextToken());
                            String white = tokenizer.nextToken();
                            String client = tokenizer.nextToken();
                            String ip = tokenizer.nextToken();
                            String sender = tokenizer.nextToken();
                            String recipient = tokenizer.nextToken();
                            String hostname = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
                            if (System.currentTimeMillis() - date.getTime() > 432000000) {
                                // Ticket vencido com mais de 5 dias.
                                type = "WHITE";
                                code = 500;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Este ticket de desbloqueio está vencido."
                                        );
                            } else if (White.containsExact(white)) {
                                type = "WHITE";
                                code = 200;
                                result = getMessageHMTL(
                                        "Página de desbloqueio do SPFBL",
                                        "Já houve liberação deste remetente '"
                                        + sender + "' pelo destinatário '"
                                        + recipient + "'."
                                        );
                            } else {
                                type = "WHITE";
                                code = 200;
                                result = getWhiteHMTL(
                                        "Para desbloquear este remetente '"
                                        + sender + "',\n"
                                        + "resolva o desafio reCAPTCHA abaixo."
                                        );
                            }
                        } catch (Exception ex) {
                            type = "WHITE";
                            code = 500;
                            result = getMessageHMTL(
                                    "Página de desbloqueio do SPFBL",
                                    "Ocorreu um erro no processamento desta solicitação: "
                                    + ex.getMessage() == null ? "undefined error." : ex.getMessage()
                                    );
                        }
                    } else {
                        type = "HTTPC";
                        code = 403;
                        result = "Forbidden\n";
                    }
                } else if (request.equals("PUT")) {
                    if (command.startsWith("/spam/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            TreeSet<String> complainSet = SPF.addComplain(origin, ticket);
                            if (complainSet == null) {
                                type = "SPFSP";
                                code = 404;
                                result = "DUPLICATE COMPLAIN\n";
                            } else {
                                type = "SPFSP";
                                code = 200;
                                String recipient = SPF.getRecipient(ticket);
                                result = "OK " + complainSet + (recipient == null ? "" : " >" + recipient) + "\n";
                            }
                        } catch (Exception ex) {
                            type = "SPFSP";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else if (command.startsWith("/ham/")) {
                        try {
                            int index = command.indexOf('/', 1) + 1;
                            String ticket = command.substring(index);
                            ticket = URLDecoder.decode(ticket, "UTF-8");
                            TreeSet<String> tokenSet = SPF.deleteComplain(origin, ticket);
                            if (tokenSet == null) {
                                type = "SPFHM";
                                code = 404;
                                result = "ALREADY REMOVED\n";
                            } else {
                                type = "SPFHM";
                                code = 200;
                                String recipient = SPF.getRecipient(ticket);
                                result = "OK " + tokenSet + (recipient == null ? "" : " >" + recipient) + "\n";
                            }
                        } catch (Exception ex) {
                            type = "SPFHM";
                            code = 500;
                            result = ex.getMessage() == null ? "Undefined error." : ex.getMessage() + "\n";
                        }
                    } else {
                        type = "HTTPC";
                        code = 403;
                        result = "Forbidden\n";
                    }
                } else {
                    type = "HTTPC";
                    code = 405;
                    result = "Method not allowed.\n";
                }
                try {
                    response(code, result, exchange);
                    command = request + " " + command;
                    result = code + " " + result;
                } catch (IOException ex) {
                    result = ex.getMessage();
                }
                Server.logQuery(
                        time, type,
                        origin,
                        command,
                        result
                        );
            } catch (Exception ex) {
                Server.logError(ex);
            } finally {
                exchange.close();
            }
        }
    }
    
    private static boolean enviarDesbloqueio(
            String url,
            String remetente,
            String destinatario
            ) {
        if (
                Core.hasSMTP()
                && Core.hasAdminEmail()
                && Domain.isEmail(destinatario)
                && url != null
                && !NoReply.contains(destinatario)
                ) {
            try {
                Server.logDebug("sending unblock by e-mail.");
                InternetAddress[] recipients = InternetAddress.parse(destinatario);
                Properties props = System.getProperties();
                Session session = Session.getDefaultInstance(props);
                MimeMessage message = new MimeMessage(session);
                message.setHeader("Date", Core.getEmailDate());
                message.setFrom(Core.getAdminEmail());
                message.setReplyTo(InternetAddress.parse(remetente));
                message.addRecipients(Message.RecipientType.TO, recipients);
                message.setSubject("Solicitação de envio SPFBL");
                // Corpo da mensagem.
                StringBuilder builder = new StringBuilder();
                builder.append("<html>\n");
                builder.append("  <head>\n");
                builder.append("    <meta charset=\"UTF-8\">\n");
                builder.append("    <title>Solicitação de envio</title>\n");
                builder.append("  </head>\n");
                builder.append("  <body>\n");
                builder.append("       O remetente '");
                builder.append(remetente);
                builder.append("' deseja lhe enviar mensagens\n");
                builder.append("       porém foi bloqueado pelo sistema como fonte de SPAM.<br>\n");
                builder.append("       Se você confia neste remetente e quer receber mensagens dele,\n");
                builder.append("       acesse esta URL e resolva o reCAPTCHA:<br>\n");
                builder.append("       <a href=\"");
                builder.append(url);
                builder.append("\">");
                builder.append(url);
                builder.append("</a><br>\n");
                builder.append("  </body>\n");
                builder.append("</html>\n");
                message.setContent(builder.toString(), "text/html;charset=UTF-8");
                message.saveChanges();
                // Enviar mensagem.
                return Core.offerMessage(message);
            } catch (Exception ex) {
                Server.logError(ex);
                return false;
            }
        } else {
            return false;
        }
    }
    
    private static String getUnblockHMTL(String message) throws ProcessException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("  <head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>Página de desbloqueio do SPFBL</title>\n");
        if (Core.hasRecaptchaKeys()) {
//             novo reCAPCHA
//            builder.append("    <script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>\n");
        }
        builder.append("  </head>\n");
        builder.append("  <body>\n");
        builder.append("    <form method=\"POST\">\n");
        builder.append("       A sua mensagem está sendo rejeitada por bloqueio manual.<br>\n");
        builder.append("       ");
//        builder.append("       Para liberar o recebimento da mensagem, resolva o desafio reCAPTCHA abaixo.<br>\n");
        StringTokenizer tokenizer = new StringTokenizer(message, "\n");
        while (tokenizer.hasMoreTokens()) {
            String line = tokenizer.nextToken();
            builder.append(line);
            builder.append("<br>\n");
        }
        if (Core.hasRecaptchaKeys()) {
            String recaptchaKeySite = Core.getRecaptchaKeySite();
            String recaptchaKeySecret = Core.getRecaptchaKeySecret();
            ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaKeySite, recaptchaKeySecret, false);
            builder.append(captcha.createRecaptchaHtml(null, null));
            // novo reCAPCHA
//            builder.append("      <div class=\"g-recaptcha\" data-sitekey=\"");
//            builder.append(recaptchaKeySite);
//            builder.append("\"></div>\n");
        }
        builder.append("       <input type=\"submit\" value=\"Liberar\">\n");
//        if (Core.hasAdminEmail()) {
//            builder.append("       Se deseja automatizar este procedimento,<br>\n");
//            builder.append("       entre em contato com <a href=\"");
//            builder.append(Core.getAdminEmail());
//            builder.append("\">");
//            builder.append(Core.getAdminEmail());
//            builder.append("</a>.<br>\n");
//        }
        builder.append("    </form>\n");
        builder.append("  </body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }
    
    private static String getWhiteHMTL(String message) throws ProcessException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("  <head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>Página de desbloqueio do SPFBL</title>\n");
        if (Core.hasRecaptchaKeys()) {
//             novo reCAPCHA
//            builder.append("    <script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>\n");
        }
        builder.append("  </head>\n");
        builder.append("  <body>\n");
        builder.append("    <form method=\"POST\">\n");
        builder.append("       Este remetente foi bloqueado no sistema SPFBL.<br>\n");
        builder.append("       ");
//        builder.append("       Para liberar o recebimento da mensagem, resolva o desafio reCAPTCHA abaixo.<br>\n");
        StringTokenizer tokenizer = new StringTokenizer(message, "\n");
        while (tokenizer.hasMoreTokens()) {
            String line = tokenizer.nextToken();
            builder.append(line);
            builder.append("<br>\n");
        }
        if (Core.hasRecaptchaKeys()) {
            String recaptchaKeySite = Core.getRecaptchaKeySite();
            String recaptchaKeySecret = Core.getRecaptchaKeySecret();
            ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaKeySite, recaptchaKeySecret, false);
            builder.append(captcha.createRecaptchaHtml(null, null));
            // novo reCAPCHA
//            builder.append("      <div class=\"g-recaptcha\" data-sitekey=\"");
//            builder.append(recaptchaKeySite);
//            builder.append("\"></div>\n");
        }
        builder.append("       <input type=\"submit\" value=\"Liberar\">\n");
//        if (Core.hasAdminEmail()) {
//            builder.append("       Se deseja automatizar este procedimento,<br>\n");
//            builder.append("       entre em contato com <a href=\"");
//            builder.append(Core.getAdminEmail());
//            builder.append("\">");
//            builder.append(Core.getAdminEmail());
//            builder.append("</a>.<br>\n");
//        }
        builder.append("    </form>\n");
        builder.append("  </body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private static String getReleaseHMTL(String message) throws ProcessException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("  <head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>Página de liberação SPFBL</title>\n");
        if (Core.hasRecaptchaKeys()) {
//             novo reCAPCHA
//            builder.append("    <script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>\n");
        }
        builder.append("  </head>\n");
        builder.append("  <body>\n");
        builder.append("    <form method=\"POST\">\n");
        builder.append("       O recebimento da sua mensagem está sendo atrasado por suspeita de SPAM.<br>\n");
        builder.append("       ");
//        builder.append("       Para liberar o recebimento da mensagem, resolva o desafio reCAPTCHA abaixo.<br>\n");
        StringTokenizer tokenizer = new StringTokenizer(message, "\n");
        while (tokenizer.hasMoreTokens()) {
            String line = tokenizer.nextToken();
            builder.append(line);
            builder.append("<br>\n");
        }
        if (Core.hasRecaptchaKeys()) {
            String recaptchaKeySite = Core.getRecaptchaKeySite();
            String recaptchaKeySecret = Core.getRecaptchaKeySecret();
            ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaKeySite, recaptchaKeySecret, false);
            builder.append(captcha.createRecaptchaHtml(null, null));
            // novo reCAPCHA
//            builder.append("      <div class=\"g-recaptcha\" data-sitekey=\"");
//            builder.append(recaptchaKeySite);
//            builder.append("\"></div>\n");
        }
        builder.append("       <input type=\"submit\" value=\"Liberar\">\n");
//        if (Core.hasAdminEmail()) {
//            builder.append("       Se deseja automatizar este procedimento,<br>\n");
//            builder.append("       entre em contato com <a href=\"");
//            builder.append(Core.getAdminEmail());
//            builder.append("\">");
//            builder.append(Core.getAdminEmail());
//            builder.append("</a>.<br>\n");
//        }
        builder.append("    </form>\n");
        builder.append("  </body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private static String getMessageHMTL(String title, String message) throws ProcessException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("  <head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>");
        builder.append(title);
        builder.append("</title>\n");
        builder.append("  </head>\n");
        builder.append("  <body>\n");
        builder.append("    ");
        StringTokenizer tokenizer = new StringTokenizer(message, "\n");
        while (tokenizer.hasMoreTokens()) {
            String line = tokenizer.nextToken();
            builder.append(line);
            builder.append("<br>\n");
        }
        builder.append("  </body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private static String getComplainHMTL(
            TreeSet<String> tokenSet,
            TreeSet<String> selectionSet,
            String message,
            boolean whiteBlockForm
            ) throws ProcessException {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>\n");
        builder.append("  <head>\n");
        builder.append("    <meta charset=\"UTF-8\">\n");
        builder.append("    <title>Página de denuncia SPFBL</title>\n");
        if (Core.hasRecaptchaKeys()) {
//             novo reCAPCHA
//            builder.append("    <script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>\n");
        }
        builder.append("  </head>\n");
        builder.append("  <body>\n");
        builder.append("    ");
        builder.append(message);
        builder.append("<br>\n");
        builder.append("    <br>\n");
        if (whiteBlockForm) {
            writeBlockFormHTML(builder, tokenSet, selectionSet);
        }
        builder.append("  </body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    private static void writeBlockFormHTML(
            StringBuilder builder,
            TreeSet<String> tokenSet,
            TreeSet<String> selectionSet
            ) throws ProcessException {
        if (!tokenSet.isEmpty()) {
            builder.append("    <form method=\"POST\">\n");
            builder.append("      Se você deseja não receber mais mensagens desta origem no futuro,<br>\n");
            builder.append("      selecione os identificadores que devem ser bloqueados definitivamente:<br>\n");
            for (String identifier : tokenSet) {
                builder.append("      <input type=\"checkbox\" name=\"identifier\" value=\"");
                builder.append(identifier);
                if (selectionSet.contains(identifier)) {
                    builder.append("\" checked>");
                } else {
                    builder.append("\">");
                }
                builder.append(identifier);
                builder.append("<br>\n");
            }
            if (Core.hasRecaptchaKeys()) {
                builder.append("      Para que sua solicitação seja aceita,<br>\n");
                builder.append("      resolva o desafio reCAPTCHA abaixo.<br>\n");
                String recaptchaKeySite = Core.getRecaptchaKeySite();
                String recaptchaKeySecret = Core.getRecaptchaKeySecret();
                ReCaptcha captcha = ReCaptchaFactory.newReCaptcha(recaptchaKeySite, recaptchaKeySecret, false);
                builder.append("      ");
                builder.append(captcha.createRecaptchaHtml(null, null).replace("\r", ""));
                // novo reCAPCHA
    //            builder.append("      <div class=\"g-recaptcha\" data-sitekey=\"");
    //            builder.append(recaptchaKeySite);
    //            builder.append("\"></div>\n");
            }
            builder.append("      <input type=\"submit\" value=\"Bloquear\">\n");
            builder.append("    </form>\n");
        }
    }

    private static void response(int code, String response,
            HttpExchange exchange) throws IOException {
        byte[] byteArray = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, byteArray.length);
        OutputStream os = exchange.getResponseBody();
        try {
            os.write(byteArray);
        } finally {
            os.close();
        }
    }

    /**
     * Inicialização do serviço.
     */
    @Override
    public void run() {
        SERVER.start();
        Server.logInfo("listening complain on HTTP port " + PORT + ".");
    }

    @Override
    protected void close() throws Exception {
        Server.logDebug("unbinding complain HTTP on port " + PORT + "...");
        SERVER.stop(1);
        Server.logInfo("complain HTTP server closed.");
    }
}