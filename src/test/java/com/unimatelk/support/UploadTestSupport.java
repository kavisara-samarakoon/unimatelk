package com.unimatelk.support;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class UploadTestSupport {
    private UploadTestSupport() {}

    public static byte[] image(String format) throws IOException {
        if (format.equals("webp")) {
            return Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
        return bytes.toByteArray();
    }

    /** Real Spring transaction/synchronization lifecycle, with no database or live service. */
    public static class Transactions extends AbstractPlatformTransactionManager {
        public boolean committed;
        public boolean rolledBack;
        public boolean failCommit;
        public Runnable beforeCommit = () -> {};

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void prepareForCommit(DefaultTransactionStatus status) { beforeCommit.run(); }

        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("Synthetic ambiguous commit failure");
            committed = true;
        }

        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
