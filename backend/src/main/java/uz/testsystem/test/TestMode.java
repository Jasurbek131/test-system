package uz.testsystem.test;

public enum TestMode {
    /** Har bir javobdan keyin to'g'ri javob va izoh darhol ko'rsatiladi. */
    LEARNING,
    /** Natijalar faqat test yakunlangach ko'rsatiladi. */
    EXAM,
    /** Blok test (DTM uslubida): bir nechta fan aralash, natija oxirida, fanlar bo'yicha ball hisoblanadi. */
    BLOCK,
    /** Kartochkalar: savol va javob ochiq ko'rsatiladi, o'quvchi o'zini baholaydi ("Bilardim" / "Bilmasdim"). */
    FLASHCARD,
    /** Blits: 60 soniyada iloji boricha ko'p savol, javob darhol ochiladi; yakunda javobsiz savollar olib tashlanadi. */
    BLITZ;

    /** Javob berilishi bilan to'g'ri javob va izoh ochiladigan (va javobni o'zgartirib bo'lmaydigan) rejimlar. */
    public boolean revealsInstantly() {
        return this == LEARNING || this == BLITZ || this == FLASHCARD;
    }
}
