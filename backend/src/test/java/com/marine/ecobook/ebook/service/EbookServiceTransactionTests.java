package com.marine.ecobook.ebook.service;

import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbookServiceTransactionTests {

    private static final long EBOOK_ID = 7L;
    private static final String OLD_COVER = "/uploads/covers/old.png";
    private static final String NEW_COVER = "/uploads/covers/new.png";

    @Mock
    private EbookMapper ebookMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CoverStorage coverStorage;

    @Mock
    private MultipartFile file;

    @InjectMocks
    private EbookService ebookService;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void removesNewCoverWhenDatabaseUpdateRollsBack() {
        when(ebookMapper.selectById(EBOOK_ID)).thenReturn(draftEbook());
        when(coverStorage.save(file)).thenReturn(NEW_COVER);
        when(ebookMapper.updateById(any(Ebook.class))).thenThrow(new IllegalStateException("database update failed"));

        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> ebookService.replaceCover(EBOOK_ID, file))
                .isInstanceOf(IllegalStateException.class);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(coverStorage).deleteQuietly(NEW_COVER);
        verify(coverStorage, never()).deleteQuietly(OLD_COVER);
    }

    @Test
    void removesOldCoverOnlyAfterCommit() {
        when(ebookMapper.selectById(EBOOK_ID)).thenReturn(draftEbook());
        when(coverStorage.save(file)).thenReturn(NEW_COVER);

        TransactionSynchronizationManager.initSynchronization();

        ebookService.replaceCover(EBOOK_ID, file);

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(coverStorage).deleteQuietly(OLD_COVER);
        verify(coverStorage, never()).deleteQuietly(NEW_COVER);
    }

    private Ebook draftEbook() {
        Ebook ebook = new Ebook();
        ebook.setId(EBOOK_ID);
        ebook.setStatus("DRAFT");
        ebook.setCoverUrl(OLD_COVER);
        return ebook;
    }

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
