package com.marine.ecobook.ebook.dto;

import java.util.List;

public record PageData<T>(long total, List<T> list) {
}
