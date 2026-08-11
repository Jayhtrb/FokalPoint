import Link from "next/link";
import { ArrowLeft, MapPin, Star, CheckCircle2 } from "lucide-react";
import { createSupabaseServerClient } from "@/lib/supabase/server";

const fallbackCreators = [
  { id: "amit-sharma", name: "Amit Sharma", type: "Photographer", city: "Mumbai", rating: 4.9, verified: true, price: 8000, image: "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=900&q=85" },
  { id: "riya-sen", name: "Riya Sen", type: "Photographer · Reels", city: "Delhi", rating: 4.8, verified: true, price: 12000, image: "https://images.unsplash.com/photo-1544005313-94ddf0288fdf?auto=format&fit=crop&w=900&q=85" },
  { id: "kabir-studios", name: "Kabir Studios", type: "Photo · Video · Reels", city: "Bengaluru", rating: 5.0, verified: true, price: 25000, image: "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=900&q=85" },
  { id: "vikram-fernandes", name: "Vikram Fernandes", type: "Travel Photographer", city: "Goa", rating: 4.6, verified: false, price: 12000, image: "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=900&q=85" },
];

export default async function DiscoverPage() {
  const supabase = await createSupabaseServerClient();
  let creators = fallbackCreators;
  if (supabase) {
    const { data } = await supabase.from("creators").select("id, creator_type, city, rating, verified, starting_price, users(name, profile_image)").order("rating", { ascending: false }).limit(24);
    if (data?.length) creators = data.map((item: any) => ({ id:item.id, name:item.users?.name ?? "Fokal Creator", type:item.creator_type, city:item.city ?? "India", rating:item.rating, verified:item.verified, price:item.starting_price, image:item.users?.profile_image || fallbackCreators[0].image }));
  }
  return <main className="shell"><header className="container nav"><Link className="logo" href="/"><span className="logoMark"/>FokalPoint</Link><Link href="/" className="btn btnGhost"><ArrowLeft size={15}/> Home</Link></header><section className="container section"><div className="sectionTitle"><div><h2>Discover your next creative partner.</h2></div><p>Profiles are designed around the things that actually matter when you hire someone: work, reputation, location and fit.</p></div><div className="creatorResults discoverGrid">{creators.map((creator) => <article className="bigCreator" key={creator.id}><img src={creator.image} alt={creator.name}/><div className="bigCreatorBody">{creator.verified && <span className="badge"><CheckCircle2 size={11}/> Verified</span>}<h3>{creator.name}</h3><div className="meta">{creator.type}</div><div className="meta"><MapPin size={11} style={{verticalAlign:"-1px"}}/> {creator.city} · <Star size={10} fill="#ffb43d" color="#ffb43d" style={{verticalAlign:"-1px"}}/> {creator.rating.toFixed(1)}</div><div className="price"><span>Starting from</span><strong>₹{Number(creator.price).toLocaleString("en-IN")}</strong></div><div style={{marginTop:14}}><Link className="btn" style={{width:"100%"}} href={`/creators/${creator.id}`}>View profile</Link></div></div></article>)}</div></section></main>;
}
