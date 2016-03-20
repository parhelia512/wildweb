package cz.wildweb.server;

import cz.wildweb.api.HttpResponse;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class HttpResponseImpl implements HttpResponse {

    private final ChannelHandlerContext channelContext;
    private final DefaultHttpResponse response;
    private final Map<String, Object> variables = new HashMap<>();
    private final HttpRequestImpl request;
    private boolean sent = false;

    public HttpResponseImpl(ChannelHandlerContext channelContext, HttpRequestImpl request) {
        this.request = request;
        this.channelContext = channelContext;
        this.response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    @Override
    public void status(int status) {
        this.response.setStatus(HttpResponseStatus.valueOf(status));
    }

    @Override
    public void header(String name, String value) {
        this.response.headers().set(name, value);
    }

    @Override
    public void send() {
        if(this.request.websocket().active()) return;
        if(this.sent) return;

        this.sent = true;
        LoggerFactory.getLogger(getClass()).debug("Sending response header");
        this.channelContext.writeAndFlush(this.response);
    }

    @Override
    public void write(Object content) {
        if(this.request.websocket().active()) return;

        this.send();
        String data = stringize(content);
        LoggerFactory.getLogger(getClass()).debug("Sending response body {}", data);
        this.channelContext.write(new DefaultHttpContent(Unpooled.wrappedBuffer(data.getBytes())));
    }

    @Override
    public void close() {
        if(this.request.websocket().active()) return;

        LoggerFactory.getLogger(getClass()).debug("Closing response");
        this.send();
        this.channelContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void close(File file) {
        if(this.request.websocket().active()) return;

        LoggerFactory.getLogger(getClass()).debug("Sending file as response");

        if(!file.exists()) {
            status(404);
            close();
            return;
        }

        RandomAccessFile raf;
        long length;
        FileChannel channel;

        try {
            raf = new RandomAccessFile(file, "r");
            length = raf.length();
            channel = raf.getChannel();
        } catch (IOException e) {
            status(500);
            close();
            return;
        }

        header("Content-Length", String.valueOf(length));

        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        header("Content-Type", mimeTypesMap.getContentType(file));

        send();

        this.channelContext.writeAndFlush(new DefaultFileRegion(channel, 0, length));

        this.channelContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void close(Object content) {
        if(this.request.websocket().active()) return;

        String data = stringize(content);
        if(!this.sent) {
            this.header(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(data.length()));
        }
        this.write(data);
        this.close();
    }

    @Override
    public void put(String name, Object value) {
        this.variables.put(name, value);
    }

    @Override
    public void render(String file) {
        if(this.request.websocket().active()) return;

        LoggerFactory.getLogger(getClass()).debug("Rendering template as response");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_23);

        File templates = new File("templates");
        if(templates.exists()) {
            try {
                configuration.setDirectoryForTemplateLoading(templates);
                configuration.setDefaultEncoding("UTF-8");
                configuration.setShowErrorTips(true);
                configuration.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Template template = configuration.getTemplate(file);
            StringWriter result = new StringWriter();
            template.process(this.variables, result);
            close(result.toString());
        } catch (IOException | TemplateException e) {
            e.printStackTrace();
        }
    }

    private String stringize(Object content) {
        if(content == null) return "";
        if(content instanceof String) {
            return (String) content;
        } else {
            return content.toString();
        }
    }


}
