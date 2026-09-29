/**
 * ES/EN message catalogs. Every user-visible string flows through i18n; no
 * component hard-codes copy in a single language (Requirement 10.3–10.5). The
 * two catalogs share the same key shape, enforced by the `MessageSchema` type.
 */

const en = {
  app: {
    title: 'MeetHalfway',
    tagline: 'Find the fairest place for everyone to meet based on actual travel time.',
  },
  language: {
    label: 'Language',
    en: 'English',
    es: 'Spanish',
  },
  form: {
    heading: 'Create a meeting',
    transportMode: 'Travel mode',
    driving: 'Driving',
    walking: 'Walking',
    participants: 'Participants',
    participantName: 'Name (optional)',
    participantAddress: 'Address',
    addParticipant: 'Add participant',
    removeParticipant: 'Remove',
    submit: 'Find meeting points',
    minParticipants: 'A meeting needs at least {min} participants.',
    maxParticipants: 'A meeting allows at most {max} participants.',
    missingLocation: 'Each participant needs a selected address.',
  },
  search: {
    placeholder: 'Start typing an address…',
    noResults: 'No matches found.',
    searching: 'Searching…',
  },
  results: {
    heading: 'Recommended meeting points',
    fastest: 'Fastest',
    minimax: 'Minimax',
    fairest: 'Fairest',
    fastestHint: 'Lowest total travel time for the group.',
    minimaxHint: 'Lowest travel time for the person who travels most.',
    fairestHint:
      'Most balanced travel times, while staying close to the fastest total. Fairness is based on real travel time, not a map midpoint.',
    point: 'Meeting point',
    perParticipant: 'Travel time per participant',
    sumTime: 'Total travel time',
    maxTime: 'Longest single trip',
    stdDev: 'Travel-time spread',
    minutes: 'min',
    empty: 'No results yet. Create a meeting to see recommendations.',
  },
  map: {
    heading: 'Map',
    loading: 'Calculating meeting points…',
    error: 'The map could not be shown because the request failed.',
    empty: 'No meeting points to display yet.',
    excludedHeading: 'Some points were left off the map:',
    excludedCandidate: 'Candidate “{id}” has an out-of-range coordinate',
    excludedOrigin: 'Origin “{id}” has an out-of-range coordinate',
  },
  outlier: {
    heading: 'One participant is far from the others',
    explanation:
      'These participants travel much longer than the rest. Compare the group with and without them, then decide — no one is removed automatically.',
    including: 'Including everyone',
    excluding: 'Excluding the outlier(s)',
    avgIncluding: 'Average travel time (including)',
    avgExcluding: 'Average travel time (excluding)',
    choose: 'You decide who to include.',
  },
  routingError: {
    heading: 'Some locations could not be routed',
    reason: 'Reason',
    action: 'Correct or remove this location, then try again.',
  },
  common: {
    loading: 'Loading…',
    retry: 'Try again',
    error: 'Something went wrong.',
  },
}

/**
 * The catalog shape: the EN catalog's structure with every leaf widened to
 * `string`, so translated catalogs (ES) can carry different text while sharing
 * the exact same key set. Type-safe `t()` keys without pinning to EN literals.
 */
export type MessageSchema = { [K in keyof typeof en]: { [P in keyof (typeof en)[K]]: string } }

const es: MessageSchema = {
  app: {
    title: 'MeetHalfway',
    tagline: 'Encuentra el lugar más justo para reunirse según el tiempo real de viaje.',
  },
  language: {
    label: 'Idioma',
    en: 'Inglés',
    es: 'Español',
  },
  form: {
    heading: 'Crear una reunión',
    transportMode: 'Modo de viaje',
    driving: 'En auto',
    walking: 'A pie',
    participants: 'Participantes',
    participantName: 'Nombre (opcional)',
    participantAddress: 'Dirección',
    addParticipant: 'Agregar participante',
    removeParticipant: 'Quitar',
    submit: 'Buscar puntos de encuentro',
    minParticipants: 'Una reunión necesita al menos {min} participantes.',
    maxParticipants: 'Una reunión permite como máximo {max} participantes.',
    missingLocation: 'Cada participante necesita una dirección seleccionada.',
  },
  search: {
    placeholder: 'Empieza a escribir una dirección…',
    noResults: 'No se encontraron coincidencias.',
    searching: 'Buscando…',
  },
  results: {
    heading: 'Puntos de encuentro recomendados',
    fastest: 'Más rápido',
    minimax: 'Minimax',
    fairest: 'Más justo',
    fastestHint: 'Menor tiempo total de viaje para el grupo.',
    minimaxHint: 'Menor tiempo de viaje para quien más se desplaza.',
    fairestHint:
      'Tiempos de viaje más equilibrados, manteniéndose cerca del menor tiempo total. La equidad se basa en el tiempo real de viaje, no en un punto medio del mapa.',
    point: 'Punto de encuentro',
    perParticipant: 'Tiempo de viaje por participante',
    sumTime: 'Tiempo total de viaje',
    maxTime: 'Viaje individual más largo',
    stdDev: 'Dispersión del tiempo de viaje',
    minutes: 'min',
    empty: 'Aún no hay resultados. Crea una reunión para ver recomendaciones.',
  },
  map: {
    heading: 'Mapa',
    loading: 'Calculando puntos de encuentro…',
    error: 'No se pudo mostrar el mapa porque la solicitud falló.',
    empty: 'Aún no hay puntos de encuentro para mostrar.',
    excludedHeading: 'Algunos puntos quedaron fuera del mapa:',
    excludedCandidate: 'El candidato «{id}» tiene una coordenada fuera de rango',
    excludedOrigin: 'El origen «{id}» tiene una coordenada fuera de rango',
  },
  outlier: {
    heading: 'Un participante está lejos de los demás',
    explanation:
      'Estos participantes viajan mucho más que el resto. Compara el grupo con y sin ellos y luego decide: nadie se elimina automáticamente.',
    including: 'Incluyendo a todos',
    excluding: 'Excluyendo al (los) participante(s) atípico(s)',
    avgIncluding: 'Tiempo de viaje promedio (incluyendo)',
    avgExcluding: 'Tiempo de viaje promedio (excluyendo)',
    choose: 'Tú decides a quién incluir.',
  },
  routingError: {
    heading: 'No se pudieron calcular algunas rutas',
    reason: 'Motivo',
    action: 'Corrige o elimina esta ubicación e inténtalo de nuevo.',
  },
  common: {
    loading: 'Cargando…',
    retry: 'Reintentar',
    error: 'Algo salió mal.',
  },
}

export const messages = { en, es }

/** Supported locales, in selector order. */
export const SUPPORTED_LOCALES = ['es', 'en'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

/** Default locale (Medellín-first market); switchable via the selector. */
export const DEFAULT_LOCALE: Locale = 'es'
