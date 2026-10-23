package com.leoni.crimpingpro.interventions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leoni.crimpingpro.common.ApiException;
import org.junit.jupiter.api.Test;

class DataUrlsTest {

  private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
  private static final String JPEG = "data:image/jpeg;base64,/9j/4AAQSkZJRg==";
  private static final String PDF = "data:application/pdf;base64,JVBERi0xLjQK";

  private static int status(Throwable t) {
    return ((ApiException) t).status().value();
  }

  @Test
  void images_et_pdf_reels_acceptes() {
    assertThat(DataUrls.check(PNG, DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, "la photo")).isEqualTo("image/png");
    assertThat(DataUrls.check(JPEG, DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, "la photo")).isEqualTo("image/jpeg");
    assertThat(DataUrls.check(PDF, DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, "le rapport")).isEqualTo("application/pdf");
  }

  @Test
  void page_html_ou_script_refuses_meme_deguises_en_image() {
    String html = "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==";
    String disguised = "data:image/png;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==";
    assertThatThrownBy(() -> DataUrls.check(html, DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
    assertThatThrownBy(() -> DataUrls.check(disguised, DataUrls.DOCUMENT_TYPES, DataUrls.MAX_FILE_BYTES, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
  }

  @Test
  void contenu_illisible_ou_format_inconnu_refuse() {
    assertThatThrownBy(() -> DataUrls.check("data:image/png;base64,@@@", DataUrls.DOCUMENT_TYPES, 1000, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
    assertThatThrownBy(() -> DataUrls.check("https://exemple.test/a.png", DataUrls.DOCUMENT_TYPES, 1000, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
    assertThatThrownBy(() -> DataUrls.check("", DataUrls.DOCUMENT_TYPES, 1000, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
  }

  @Test
  void fichier_trop_volumineux_413() {
    assertThatThrownBy(() -> DataUrls.check(PNG, DataUrls.DOCUMENT_TYPES, 10, "la photo"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(413));
  }

  @Test
  void signature_limitee_aux_images_png_ou_jpeg() {
    assertThatThrownBy(() -> DataUrls.check(PDF, DataUrls.SIGNATURE_TYPES, DataUrls.MAX_SIGNATURE_BYTES, "la signature"))
        .satisfies(t -> assertThat(status(t)).isEqualTo(400));
  }
}
