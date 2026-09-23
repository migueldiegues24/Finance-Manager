package com.miguel.financemanager.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

// IP do cliente para os limites de tentativas.
//
// Por omissão usa o endereço da ligação (remoteAddr). Atrás de um proxy (ex.:
// Railway) esse é o IP do proxy, igual para todos os visitantes, e o limite
// por IP bloquearia toda a gente ao mesmo tempo; por isso, com
// TRUST_X_FORWARDED_FOR=true, usa o último valor do X-Forwarded-For, o que o
// proxy acrescentou com o IP que viu. Os valores anteriores são ignorados
// porque vêm do cliente e podem ser inventados. Só ligar com um proxy à
// frente: sem proxy, o próprio cliente escolhia o IP e fugia ao limite.
//
// IPv6 é agrupado por /64 (o bloco que um cliente normalmente recebe), senão
// quem tem um /64 teria milhões de "IPs" para gastar.
@Component
public class ClientIpResolver {

    private static final Pattern IPV4 =
            Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${auth.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = lastForwardedFor(request.getHeader("X-Forwarded-For"));
            String normalized = forwarded == null ? null : normalize(forwarded);
            if (normalized != null) {
                return normalized;
            }
        }
        String remote = normalize(request.getRemoteAddr());
        return remote != null ? remote : request.getRemoteAddr();
    }

    private static String lastForwardedFor(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.substring(header.lastIndexOf(',') + 1).trim();
    }

    // IPv4 tal como está; IPv6 reduzido ao prefixo /64; null se não for um IP.
    static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        if (IPV4.matcher(candidate).matches()) {
            return candidate;
        }
        if (candidate.indexOf(':') < 0 || !IPV6_CHARS.matcher(candidate).matches()) {
            return null;
        }
        try {
            // Entre [] o JDK só aceita um literal IPv6; nunca faz pesquisa DNS.
            InetAddress address = InetAddress.getByName("[" + candidate + "]");
            if (!(address instanceof Inet6Address)) {
                return address.getHostAddress(); // IPv4 mapeado (::ffff:a.b.c.d)
            }
            byte[] bytes = address.getAddress();
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
            }
            return prefix.append(":/64").toString();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
