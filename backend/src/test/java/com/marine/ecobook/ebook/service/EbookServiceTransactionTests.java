package com.marine.ecobook.ebook.service;

import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.mapper.ChapterMapper;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    private ChapterMapper chapterMapper;

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

    @Test
    void deletesChaptersBeforeDeletingDraftEbook() {
        when(ebookMapper.selectById(EBOOK_ID)).thenReturn(draftEbook());

        ebookService.delete(EBOOK_ID);

        InOrder deletionOrder = inOrder(chapterMapper, ebookMapper);
        deletionOrder.verify(chapterMapper).deleteByEbookId(EBOOK_ID);
        deletionOrder.verify(ebookMapper).deleteById(EBOOK_ID);
    }

    @Test
    void rejectsRootCategoryAsAnEbookFilter() {
        Category rootCategory = new Category();
        rootCategory.setId(3L);
        rootCategory.setParentId(null);
        when(categoryMapper.selectById(3L)).thenReturn(rootCategory);

        assertThatThrownBy(() -> ebookService.listAdmin(3L, null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("电子书必须归属二级分类");
    }

    @Test
    void rejectsHistoricalSummaryLongerThanPublicationLimit() {
        Ebook ebook = draftEbook();
        ebook.setTitle("测试电子书");
        ebook.setSummary("内容".repeat(251));
        ebook.setSourceNote("测试来源");
        when(ebookMapper.selectById(EBOOK_ID)).thenReturn(ebook);

        assertThatThrownBy(() -> ebookService.publish(EBOOK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("发布前请填写 20–500 字简介");
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
