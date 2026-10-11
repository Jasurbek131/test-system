package uz.testsystem.test;

public enum TestMode {
    /** Har bir javobdan keyin to'g'ri javob va izoh darhol ko'rsatiladi. */
    LEARNING,
    /** Natijalar faqat test yakunlangach ko'rsatiladi. */
    EXAM,
    /** Blok test (DTM uslubida): bir nechta fan aralash, natija oxirida, fanlar bo'yicha ball hisoblanadi. */
    BLOCK
}
