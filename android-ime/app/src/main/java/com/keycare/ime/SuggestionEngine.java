package com.keycare.ime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * SuggestionEngine - Generates varied rewrite suggestions per tone (Calm/Firm/Educational).
 * Features:
 * - Pool of 15-30 templates per tone
 * - History cache to avoid repetition
 * - Basic context heuristics for matching message type
 * - Multi-language support (EN/FR/AR)
 */
public class SuggestionEngine {

    // History cache to track recently shown suggestions (avoid repeats)
    private static final int HISTORY_SIZE = 9;
    private final LinkedList<String> historyCache = new LinkedList<>();
    
    private final Random random = new Random();

    // Language codes
    public static final String LANG_EN = "en";
    public static final String LANG_FR = "fr";
    public static final String LANG_AR = "ar";

    // Risk levels
    public enum RiskState {
        SAFE, RISKY, DANGER
    }

    // ==================== ENGLISH TEMPLATES ====================
    
    private static final String[] EN_CALM = {
        "I understand your perspective, and I'd like to discuss this calmly.",
        "Let's take a moment to find common ground here.",
        "I hear what you're saying. Can we talk about this peacefully?",
        "I appreciate you sharing your thoughts. Let me respond thoughtfully.",
        "I'd like to address this in a calm and respectful way.",
        "Let's pause and approach this with understanding.",
        "I value our communication. Can we discuss this gently?",
        "I want to understand your point of view better.",
        "Thank you for expressing yourself. Let's work through this together.",
        "I'm open to hearing more. Let's keep this conversation positive.",
        "I sense there's some tension. How can we resolve this peacefully?",
        "Let's both take a breath and communicate with kindness.",
        "I respect your feelings and want to address them calmly.",
        "Perhaps we can find a solution that works for both of us.",
        "I'm here to listen and understand, not to argue."
    };
    
    private static final String[] EN_FIRM = {
        "I need to be clear about my boundaries on this.",
        "This is not acceptable behavior, and I won't tolerate it.",
        "I'm setting a firm boundary here. Please respect it.",
        "Let me be direct: this needs to change.",
        "I expect better treatment going forward.",
        "I'm standing firm on this matter.",
        "This crosses a line that I'm not willing to accept.",
        "I need you to understand that this is non-negotiable.",
        "I'm asking you to stop this behavior immediately.",
        "Let's be clear: I deserve respect.",
        "I'm drawing the line here. This needs to end.",
        "My position on this is firm and final.",
        "I will not accept being treated this way.",
        "This is unacceptable. I require change.",
        "I'm communicating clearly: this must stop."
    };
    
    private static final String[] EN_EDUCATIONAL = {
        "Did you know that respectful communication leads to better outcomes?",
        "Studies show that calm discussions resolve conflicts more effectively.",
        "Words can have lasting impacts. Let's choose them wisely.",
        "Effective communication starts with mutual respect.",
        "Understanding different perspectives enriches our conversations.",
        "Research suggests that empathy improves relationship quality.",
        "Constructive dialogue requires active listening from all parties.",
        "Kind words can transform difficult conversations.",
        "Communication experts recommend pausing before responding emotionally.",
        "Building trust starts with respectful exchanges.",
        "Healthy boundaries are essential for positive relationships.",
        "Emotional intelligence involves recognizing the impact of our words.",
        "Conflict resolution works best when both sides feel heard.",
        "Mindful communication can prevent misunderstandings.",
        "The way we express ourselves shapes our relationships."
    };

    // Context-specific templates for insults
    private static final String[] EN_APOLOGY = {
        "I apologize if my words came across harshly. Let me rephrase.",
        "I'm sorry, that didn't come out right. What I meant was...",
        "Please forgive my earlier tone. Let me start over.",
        "I regret my choice of words. Let me try again more respectfully."
    };

    // Context-specific templates for threats
    private static final String[] EN_SAFETY = {
        "I'm concerned about the direction of this conversation. Let's step back.",
        "This type of language isn't productive. I'd prefer constructive discussion.",
        "I value my safety and well-being. Let's communicate respectfully.",
        "Threatening language isn't acceptable. Please communicate appropriately."
    };

    // ==================== FRENCH TEMPLATES ====================
    
    private static final String[] FR_CALM = {
        "Je comprends ton point de vue. Discutons-en calmement.",
        "Prenons un moment pour trouver un terrain d'entente.",
        "J'entends ce que tu dis. Pouvons-nous en parler paisiblement ?",
        "J'apprécie que tu partages tes pensées. Laisse-moi répondre avec réflexion.",
        "Je voudrais aborder cela de manière calme et respectueuse.",
        "Faisons une pause et approchons cela avec compréhension.",
        "Je valorise notre communication. Pouvons-nous discuter gentiment ?",
        "Je veux mieux comprendre ton point de vue.",
        "Merci de t'exprimer. Travaillons ensemble pour résoudre cela.",
        "Je suis ouvert à en entendre plus. Gardons cette conversation positive.",
        "Je sens une tension. Comment pouvons-nous résoudre cela pacifiquement ?",
        "Respirons tous les deux et communiquons avec bienveillance.",
        "Je respecte tes sentiments et veux les aborder calmement.",
        "Peut-être pouvons-nous trouver une solution qui convient à tous.",
        "Je suis là pour écouter et comprendre, pas pour argumenter."
    };
    
    private static final String[] FR_FIRM = {
        "Je dois être clair sur mes limites à ce sujet.",
        "Ce comportement n'est pas acceptable et je ne le tolérerai pas.",
        "Je fixe une limite ferme ici. Veuillez la respecter.",
        "Laisse-moi être direct : cela doit changer.",
        "J'attends un meilleur traitement à l'avenir.",
        "Je reste ferme sur cette question.",
        "Cela dépasse une ligne que je ne suis pas prêt à accepter.",
        "J'ai besoin que tu comprennes que c'est non négociable.",
        "Je te demande d'arrêter ce comportement immédiatement.",
        "Soyons clairs : je mérite le respect.",
        "Je trace la ligne ici. Cela doit cesser.",
        "Ma position sur ce sujet est ferme et définitive.",
        "Je n'accepterai pas d'être traité de cette façon.",
        "C'est inacceptable. J'exige un changement.",
        "Je communique clairement : cela doit s'arrêter."
    };
    
    private static final String[] FR_EDUCATIONAL = {
        "Savais-tu que la communication respectueuse mène à de meilleurs résultats ?",
        "Les études montrent que les discussions calmes résolvent mieux les conflits.",
        "Les mots peuvent avoir des impacts durables. Choisissons-les avec soin.",
        "Une communication efficace commence par le respect mutuel.",
        "Comprendre différentes perspectives enrichit nos conversations.",
        "La recherche suggère que l'empathie améliore la qualité des relations.",
        "Le dialogue constructif nécessite une écoute active de tous.",
        "Les mots gentils peuvent transformer les conversations difficiles.",
        "Les experts recommandent de faire une pause avant de répondre émotionnellement.",
        "Construire la confiance commence par des échanges respectueux.",
        "Des limites saines sont essentielles pour des relations positives.",
        "L'intelligence émotionnelle implique de reconnaître l'impact de nos mots.",
        "La résolution de conflits fonctionne mieux quand les deux côtés se sentent entendus.",
        "La communication consciente peut prévenir les malentendus.",
        "La façon dont nous nous exprimons façonne nos relations."
    };

    // ==================== ARABIC TEMPLATES ====================
    
    private static final String[] AR_CALM = {
        "أفهم وجهة نظرك، دعنا نناقش هذا بهدوء.",
        "لنأخذ لحظة لإيجاد أرضية مشتركة.",
        "أسمع ما تقوله. هل يمكننا التحدث عن هذا بسلام؟",
        "أقدر مشاركتك لأفكارك. دعني أرد بتمعن.",
        "أود معالجة هذا بطريقة هادئة ومحترمة.",
        "لنتوقف ونتعامل مع هذا بتفهم.",
        "أقدر تواصلنا. هل يمكننا مناقشة هذا بلطف؟",
        "أريد أن أفهم وجهة نظرك بشكل أفضل.",
        "شكراً للتعبير عن نفسك. لنعمل معاً على هذا.",
        "أنا منفتح لسماع المزيد. لنحافظ على إيجابية المحادثة.",
        "أشعر ببعض التوتر. كيف يمكننا حل هذا بسلام؟",
        "لنأخذ نفساً عميقاً ونتواصل بلطف.",
        "أحترم مشاعرك وأريد معالجتها بهدوء.",
        "ربما يمكننا إيجاد حل يناسبنا جميعاً.",
        "أنا هنا للاستماع والفهم، وليس للجدال."
    };
    
    private static final String[] AR_FIRM = {
        "أحتاج أن أكون واضحاً بشأن حدودي في هذا الأمر.",
        "هذا السلوك غير مقبول ولن أتسامح معه.",
        "أضع حداً صارماً هنا. يرجى احترامه.",
        "دعني أكون صريحاً: هذا يحتاج إلى تغيير.",
        "أتوقع معاملة أفضل في المستقبل.",
        "أقف بثبات في هذا الأمر.",
        "هذا يتجاوز خطاً لست مستعداً لقبوله.",
        "أحتاج منك أن تفهم أن هذا غير قابل للتفاوض.",
        "أطلب منك إيقاف هذا السلوك فوراً.",
        "لنكن واضحين: أستحق الاحترام.",
        "أرسم الخط هنا. هذا يجب أن ينتهي.",
        "موقفي في هذا الأمر ثابت ونهائي.",
        "لن أقبل أن أُعامل بهذه الطريقة.",
        "هذا غير مقبول. أطلب التغيير.",
        "أتواصل بوضوح: هذا يجب أن يتوقف."
    };
    
    private static final String[] AR_EDUCATIONAL = {
        "هل تعلم أن التواصل المحترم يؤدي إلى نتائج أفضل؟",
        "تظهر الدراسات أن النقاشات الهادئة تحل النزاعات بفعالية أكبر.",
        "الكلمات يمكن أن يكون لها تأثيرات دائمة. لنختارها بحكمة.",
        "التواصل الفعال يبدأ بالاحترام المتبادل.",
        "فهم وجهات النظر المختلفة يثري محادثاتنا.",
        "تشير الأبحاث إلى أن التعاطف يحسن جودة العلاقات.",
        "الحوار البناء يتطلب الاستماع الفعال من جميع الأطراف.",
        "الكلمات اللطيفة يمكن أن تحول المحادثات الصعبة.",
        "يوصي الخبراء بالتوقف قبل الرد عاطفياً.",
        "بناء الثقة يبدأ بالتبادلات المحترمة.",
        "الحدود الصحية ضرورية للعلاقات الإيجابية.",
        "الذكاء العاطفي يتضمن التعرف على تأثير كلماتنا.",
        "حل النزاعات يعمل بشكل أفضل عندما يشعر كلا الطرفين بأنهما مسموعان.",
        "التواصل الواعي يمكن أن يمنع سوء الفهم.",
        "الطريقة التي نعبر بها عن أنفسنا تشكل علاقاتنا."
    };

    // ==================== KEYWORD DETECTION ====================
    
    // English keywords for context detection
    private static final String[] EN_INSULT_KEYWORDS = {
        "stupid", "idiot", "moron", "dumb", "fool", "hate you", "loser", "pathetic"
    };
    
    private static final String[] EN_THREAT_KEYWORDS = {
        "kill", "hurt", "destroy", "ruin", "regret", "pay for", "watch out", "threat"
    };
    
    private static final String[] EN_ANGER_KEYWORDS = {
        "angry", "furious", "mad", "pissed", "upset", "frustrated", "sick of"
    };

    // ==================== PUBLIC API ====================

    /**
     * Generate 3 unique suggestions based on text, language, and risk state.
     * 
     * @param text The input text to analyze
     * @param lang Language code (en, fr, ar)
     * @param risk Risk state (SAFE, RISKY, DANGER)
     * @return SuggestionResult with calm, firm, and educational suggestions
     */
    public SuggestionResult generate(String text, String lang, RiskState risk) {
        String textLower = text != null ? text.toLowerCase() : "";
        
        // Detect context
        MessageContext context = detectContext(textLower);
        
        // Get templates for language
        String[] calmPool = getCalmPool(lang, context);
        String[] firmPool = getFirmPool(lang, context);
        String[] eduPool = getEducationalPool(lang);
        
        // Pick unique suggestions that aren't in history
        String calm = pickUnique(calmPool);
        String firm = pickUnique(firmPool);
        String educational = pickUnique(eduPool);
        
        // Add to history
        addToHistory(calm);
        addToHistory(firm);
        addToHistory(educational);
        
        // Log for sanity check
        logSanityCheck(text, lang, risk, calm, firm, educational);
        
        return new SuggestionResult(calm, firm, educational);
    }

    /**
     * Clear suggestion history (useful for testing or reset)
     */
    public void clearHistory() {
        historyCache.clear();
    }

    // ==================== PRIVATE HELPERS ====================

    private enum MessageContext {
        NEUTRAL,
        INSULT,
        THREAT,
        ANGER
    }

    private MessageContext detectContext(String textLower) {
        // Check for threats first (most serious)
        for (String keyword : EN_THREAT_KEYWORDS) {
            if (textLower.contains(keyword)) {
                return MessageContext.THREAT;
            }
        }
        
        // Check for insults
        for (String keyword : EN_INSULT_KEYWORDS) {
            if (textLower.contains(keyword)) {
                return MessageContext.INSULT;
            }
        }
        
        // Check for anger
        for (String keyword : EN_ANGER_KEYWORDS) {
            if (textLower.contains(keyword)) {
                return MessageContext.ANGER;
            }
        }
        
        return MessageContext.NEUTRAL;
    }

    private String[] getCalmPool(String lang, MessageContext context) {
        String[] base;
        switch (lang) {
            case LANG_FR:
                base = FR_CALM;
                break;
            case LANG_AR:
                base = AR_CALM;
                break;
            default:
                // For English, add context-specific templates
                if (context == MessageContext.INSULT) {
                    return combineArrays(EN_CALM, EN_APOLOGY);
                }
                base = EN_CALM;
                break;
        }
        return base;
    }

    private String[] getFirmPool(String lang, MessageContext context) {
        switch (lang) {
            case LANG_FR:
                return FR_FIRM;
            case LANG_AR:
                return AR_FIRM;
            default:
                // For English, add safety templates if threat detected
                if (context == MessageContext.THREAT) {
                    return combineArrays(EN_FIRM, EN_SAFETY);
                }
                return EN_FIRM;
        }
    }

    private String[] getEducationalPool(String lang) {
        switch (lang) {
            case LANG_FR:
                return FR_EDUCATIONAL;
            case LANG_AR:
                return AR_EDUCATIONAL;
            default:
                return EN_EDUCATIONAL;
        }
    }

    private String[] combineArrays(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private String pickUnique(String[] pool) {
        if (pool == null || pool.length == 0) {
            return "No suggestion available";
        }
        
        // Shuffle a copy of the pool
        List<String> shuffled = new ArrayList<>();
        for (String s : pool) {
            shuffled.add(s);
        }
        Collections.shuffle(shuffled, random);
        
        // Find first one not in history
        for (String suggestion : shuffled) {
            if (!historyCache.contains(suggestion)) {
                return suggestion;
            }
        }
        
        // All in history? Just pick random from pool
        return shuffled.get(random.nextInt(shuffled.size()));
    }

    private void addToHistory(String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) return;
        
        // Remove if already exists (move to end)
        historyCache.remove(suggestion);
        
        // Add to end
        historyCache.addLast(suggestion);
        
        // Trim to max size
        while (historyCache.size() > HISTORY_SIZE) {
            historyCache.removeFirst();
        }
    }

    private void logSanityCheck(String text, String lang, RiskState risk, 
                                String calm, String firm, String educational) {
        // Simple logging for debugging - can be expanded
        System.out.println("[SuggestionEngine] Input: \"" + truncate(text, 30) + "\"");
        System.out.println("[SuggestionEngine] Lang: " + lang + ", Risk: " + risk);
        System.out.println("[SuggestionEngine] Calm: " + truncate(calm, 40));
        System.out.println("[SuggestionEngine] Firm: " + truncate(firm, 40));
        System.out.println("[SuggestionEngine] Edu: " + truncate(educational, 40));
        System.out.println("[SuggestionEngine] History size: " + historyCache.size());
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    // ==================== RESULT CLASS ====================

    public static class SuggestionResult {
        public final String calm;
        public final String firm;
        public final String educational;

        public SuggestionResult(String calm, String firm, String educational) {
            this.calm = calm;
            this.firm = firm;
            this.educational = educational;
        }
    }
}
