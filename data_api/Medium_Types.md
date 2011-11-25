# Medium Types in kooaba Data API
Data API Version 1.1 (2010-12-20)

Items can have a Medium object assigned to them. This defines the kind of item. For instance, a Wine, a Book Cover, a Newspaper page, etc. Specifying the Medium helps the recognition engine to optimize certain parameters which are optimized for the kind of item. Furthermore, it allows you to set Medium specific meta-data fields. For example the author of a book, or the vintage of a wine. We need to configure the available Medium Types for your group, so please contact us if you want to make use of this feature. The available Medium Types are:

    Book                Book Cover
    DVD                 DVD Cover
    Game                Game Cover
    Movie               Movie Poster
    Album               CD Cover
    Ad                  Print Advertising
    BillboardPoster     Billboard
    ConferencePoster    Scientific Poster
    Art                 Piece of Art (painting etc.)
    PeriodicalPage      Page in a Newspaper or Magazine
    CataloguePage       Page in a printed Catalogue
    Wine                A wine label
    Postcard            A Postcard

Optional and mandatory fields of the Medium Types are listed in the sections below. Some of the fields can be addressed by multiple names, in which case the alternative fields are listed in brackets. The default field type is string with limit of 255 characters. Other field types are marked explicitly in curly brackets (e.g. `{integer}`), optionally with size limit (e.g. `{string(16)}`).

---

## Ad

Optional fields:

    advertiser
    advertising_agency
    brand
    campaign (title)
    campaign_ref  {integer}
    media_agency
    provider
    start_date  {date}
    type

---

## Album

Mandatory fields:

    title

Optional fields:

    aasin
    artist
    genre
    locale
    released_on (releasedate)  {date}
    salesrank  {integer}

---

## Art

Mandatory fields:

    artist
    title

Optional fields:

    year  {integer}

---

## BillboardPoster

Mandatory fields:

    provider

Optional fields:

    advertiser
    advertising_agency
    brand
    campaign (title)
    campaign_ref  {integer}
    media_agency
    start_date  {date}
    type

---

## Book

Mandatory fields:

    author (artist)
    title

Optional fields:

    aasin
    locale
    published_on (releasedate)  {date}
    publisher

---

## CataloguePage

Optional fields:

    catalogue_issue
    catalogue_kind
    catalogue_number  {integer}
    catalogue_publisher
    catalogue_title
    number  {integer}
    published_on  {date}

---

## ConferencePoster

Mandatory fields:

    conference_name

Optional fields:

    authors
    conference_end_date  {date}
    conference_start_date  {date}
    organisation
    title

---

## DVD

Mandatory fields:

    title

Optional fields:

    aasin
    locale
    movie_released_on  {date}
    released_on (releasedate)  {date}
    salesrank  {integer}

---

## Game

Mandatory fields:

    title

Optional fields:

    aasin
    released_on (releasedate)  {date}
    locale

---

## Movie

Mandatory fields:

    title

Optional fields:

    released_on  {date}

---

## PeriodicalPage

The `periodical_issue` field cannot contain dot (.), question mark (?) or slash (/).

Mandatory fields:

    periodical_issue
    periodical_title

Optional fields:

    number  {integer}
    periodical_issn
    periodical_number  {integer}
    periodical_region  {string(16)}
    periodical_volume  {integer}
    published_on  {date}

---

## Postcard

Mandatory fields:

    customer
    title
    year  {integer}

---

## Wine

Mandatory fields:

    title

Optional fields:

    food_pairing
    price
    rating
    region
    varietal
    winery
    year  {integer}

